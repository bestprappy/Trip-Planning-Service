package com.navio.tripplanningservice.service.publication;

import com.github.benmanes.caffeine.cache.*;
import com.navio.tripplanningservice.dto.publication.*;
import com.navio.tripplanningservice.model.TripPublication;
import com.navio.tripplanningservice.repository.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Stream;
import static com.navio.tripplanningservice.service.publication.SharedPlanSearchService.*;

/** One public catalogue read; all phrase support checks reuse the same immutable evidence. */
final class SmartPlanSearchEngine {
 private final SharedPlanSearchService reader;
 private final ExploreSearchRepository repository;
 private final ExplorePlanSummarizer summarizer;
 private final SharedPlanCopyRepository copies;
 private record Parsed(PublicPlanSnapshot plan,PublicSearchEvidence evidence,int weight) {}
 private record Entry(TripPublication row,Parsed parsed) {}
 private record Ranked(Entry entry,PlanSearchQuery.Match match) {}
 private static final Cache<String,Parsed> CACHE=Caffeine.newBuilder().maximumWeight(32L*1024*1024).weigher((String key,Parsed p)->p.weight()).build();
 SmartPlanSearchEngine(SharedPlanSearchService reader,ExploreSearchRepository repository,ExplorePlanSummarizer summarizer,SharedPlanCopyRepository copies){this.reader=reader;this.repository=repository;this.summarizer=summarizer;this.copies=copies;}
 private static String digest(String text){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}
 private Parsed parse(TripPublication row){
  if(!Boolean.TRUE.equals(row.getListedInExplore())||!row.isActive()||row.getSanitizerVersion()==null||row.getSanitizerVersion()<PlanPublicationSanitizer.SANITIZER_VERSION||row.getSnapshot()==null)return null;
  String key=row.getId()+":"+row.getRevision()+":"+row.getSanitizerVersion()+":"+TravelConcepts.DATA.version()+":"+digest(row.getSnapshot());
  Parsed cached=CACHE.getIfPresent(key);if(cached!=null)return cached;
  var plan=reader.read(row);if(plan==null)return null;
  var parsed=new Parsed(plan,PublicSearchEvidenceBuilder.build(plan,SharedPlanSearchService::code),Math.max(512,row.getSnapshot().length()*6));
  if(CACHE.estimatedSize()<5000)CACHE.put(key,parsed);return parsed;
 }
 static <T> List<T> window(List<T> values,int start,int size){int from=Math.min(values.size(),start);return values.subList(from,Math.min(values.size(),from+size));}
 SearchPage search(String term,String country,Integer days,int page,int size,String sort){
  return search(term,country,days,page,size,sort,PlanDiscovery.Filters.NONE);
 }
 SearchPage search(String term,String country,Integer days,int page,int size,String sort,PlanDiscovery.Filters filters){
  term=Objects.toString(term,"").strip();country=Objects.toString(country,"").toUpperCase(Locale.ROOT);
  if(term.codePointCount(0,term.length())>256||!country.isEmpty()&&!COUNTRIES.containsKey(country)||days!=null&&(days<1||days>36500)||page<0||page>500||size<1||size>48)throw new IllegalArgumentException("Invalid search parameters");
  var priorities=PlanSortOrder.parse(sort);
  long began=System.nanoTime();var entries=new ArrayList<Entry>();
  try(Stream<TripPublication> stream=repository.streamListed(PlanPublicationSanitizer.SANITIZER_VERSION)){stream.forEach(row->{var parsed=parse(row);if(parsed!=null)entries.add(new Entry(row,parsed));});}
  var today=reader.discoveryToday();
  var activity=reader.activityScores(today);
  var allCopies=entries.isEmpty()?Map.<UUID,SharedPlanCopyRepository.CopyStats>of():copies.counts(entries.stream().map(e->e.row().getId()).toList());
  boolean trendingCollection=filters.collections().contains("trending");
  entries.removeIf(e->!filters.accepts(e.parsed().evidence(),today));
  var locations=new LinkedHashMap<String,PlanSearchQuery.Location>();COUNTRIES.forEach((code,name)->locations.put("country:"+code,new PlanSearchQuery.Location(name,code,true)));
  String normalizedTerm=normal(term);
  CODES.forEach((alias,code)->{if(alias.length()>2||Set.of("uk","us").contains(alias)||normalizedTerm.equals(alias))locations.putIfAbsent("alias:"+alias,new PlanSearchQuery.Location(alias,code,true));});
  locations.put("california",new PlanSearchQuery.Location("California","US",false));locations.put("hua-hin",new PlanSearchQuery.Location("Hua Hin","TH",false));
  for(var entry:entries){var plan=entry.parsed().plan();String c=code(plan.destinationCountry());if(!c.isBlank()&&plan.destinationCity()!=null)locations.putIfAbsent(normal(plan.destinationCity())+":"+c,new PlanSearchQuery.Location(plan.destinationCity(),c,false));
   for(var day:Stream.concat(list(plan.days()).stream(),list(plan.lists()).stream()).filter(Objects::nonNull).toList())for(var item:list(day.items()))if(item!=null&&item.city()!=null){String placeCountry=c;if(item.address()!=null){String[] parts=item.address().split(",");String found=code(parts[parts.length-1]);if(!found.isBlank())placeCountry=found;}if(!placeCountry.isBlank())locations.putIfAbsent(normal(item.city())+":"+placeCountry,new PlanSearchQuery.Location(item.city(),placeCountry,false));}}
  String selected=country;var intent=SearchIntentParser.parse(term,locations.values(),country,days);
  // Preserve recent-activity leaders; fallbacks never bypass the eligibility checks below.
  Function<Ranked,Integer> tier=r->activity.getOrDefault(r.entry().row().getId(),0L)>0?0:
      allCopies.getOrDefault(r.entry().row().getId(),SharedPlanCopyRepository.CopyStats.ZERO).copies()>0||Objects.requireNonNullElse(r.entry().row().getViewCount(),0L)>0?1:2;
  Comparator<Ranked> order=trendingCollection?Comparator.comparingInt(tier::apply):(a,b)->0;
  for(String priority:priorities){
   Comparator<Ranked> metric=switch(priority){
    case "relevance" -> Comparator.comparingInt((Ranked r)->r.match().score()).reversed();
    case "recent" -> Comparator.comparing((Ranked r)->r.entry().row().getListedAt(),Comparator.nullsLast(Comparator.reverseOrder()));
    case "trending" -> {
      Comparator<Ranked> activityOrder=Comparator.comparingLong((Ranked r)->activity.getOrDefault(r.entry().row().getId(),0L)).reversed();
      if(trendingCollection&&priorities.size()==1)activityOrder=activityOrder.thenComparing(Comparator.comparingLong((Ranked r)->allCopies.getOrDefault(r.entry().row().getId(),SharedPlanCopyRepository.CopyStats.ZERO).copies()).reversed())
          .thenComparing(Comparator.comparingLong((Ranked r)->Objects.requireNonNullElse(r.entry().row().getViewCount(),0L)).reversed());
      yield activityOrder;
    }
    case "popular" -> Comparator.comparingLong((Ranked r)->allCopies.getOrDefault(r.entry().row().getId(),SharedPlanCopyRepository.CopyStats.ZERO).copies()).reversed();
    case "views" -> Comparator.comparingLong((Ranked r)->Objects.requireNonNullElse(r.entry().row().getViewCount(),0L)).reversed();
    case "discussed" -> Comparator.comparingLong((Ranked r)->Objects.requireNonNullElse(r.entry().row().getCommentCount(),0L)).reversed();
    case "rating" -> Comparator.comparingDouble((Ranked r)->r.entry().row().getRatingCount()==null||r.entry().row().getRatingCount()==0?-1:(double)r.entry().row().getRatingSum()/r.entry().row().getRatingCount()).reversed();
    default -> throw new IllegalArgumentException("Invalid sorting priorities");
   };
   order=order.thenComparing(metric);
  }
  // Explicit secondary priorities must precede the legacy intrinsic tie-breakers.
  if(priorities.contains("rating"))order=order.thenComparing(Comparator.comparingLong((Ranked r)->Objects.requireNonNullElse(r.entry().row().getRatingCount(),0L)).reversed());
  if(priorities.contains("popular")&&!priorities.contains("views"))order=order.thenComparing(Comparator.comparingLong((Ranked r)->Objects.requireNonNullElse(r.entry().row().getViewCount(),0L)).reversed());
  if(filters.collections().contains("events"))order=order.thenComparingLong(r->PlanDiscovery.eventProximity(r.entry().parsed().evidence(),today));
  order=order.thenComparing(r->r.entry().row().getListedAt(),Comparator.nullsLast(Comparator.reverseOrder())).thenComparing(r->r.entry().row().getToken());
  var matching=new ArrayList<Ranked>();var relevant=new ArrayList<Ranked>();
  for(var entry:entries){if(!selected.isBlank()&&!entry.parsed().evidence().countries().contains(selected))continue;var match=PlanSearchEligibility.evaluate(intent,entry.parsed().evidence(),entry.parsed().plan().dayCount());if(match!=null)(match.strong()?matching:relevant).add(new Ranked(entry,match));}
  matching.sort(order);relevant.sort(order);
  var suggestions=QuerySuggestionComposer.compose(intent,locations.values(),selected,days,candidate->entries.stream().filter(e->selected.isBlank()||e.parsed().evidence().countries().contains(selected)).filter(e->PlanSearchEligibility.evaluate(candidate,e.parsed().evidence(),e.parsed().plan().dayCount())!=null).count());
  var retained=new LinkedHashMap<UUID,Ranked>();int capacity=page*size+size+6;
  Stream.concat(window(matching,0,capacity).stream(),window(relevant,0,capacity).stream()).forEach(r->retained.put(r.entry().row().getId(),r));
  var copyStats=allCopies;
  Function<Ranked,ExplorePlanSummary> summary=r->{var e=r.entry();var p=e.parsed().plan();var row=e.row();return summarizer.summarize(row.getToken(),p,row.getAuthorDisplayName(),row.getListedAt(),row.getUpdatedAt(),Objects.requireNonNullElse(row.getViewCount(),0L),copyStats.getOrDefault(row.getId(),SharedPlanCopyRepository.CopyStats.ZERO).copies()).withFeedback(row);};
  int recommended=!term.isBlank()&&sort.equals("relevance")?Math.min(6,matching.size()):0;
  List<ExplorePlanSummary> strongPage=window(matching,page*size,size).stream().map(summary).toList(),weakPage=window(relevant,page*size,size).stream().map(summary).toList();
  var discoveryLabels=new LinkedHashMap<String,String>();
  if(trendingCollection)Stream.concat(window(matching,page*size,size).stream(),window(relevant,page*size,size).stream())
      .forEach(r->discoveryLabels.put(r.entry().row().getToken(),switch(tier.apply(r)){case 0->"trending";case 1->"popular";default->"recent";}));
  var reasons=new LinkedHashMap<String,String>();Stream.concat(window(relevant,page*size,size).stream(),window(relevant,0,4).stream()).forEach(r->reasons.put(r.entry().row().getToken(),r.match().reason()));
  var destinations=new LinkedHashMap<String,Destination>();String query=normal(term);
  for(var location:locations.values()){
   if(location.name().length()<3||!selected.isBlank()&&!selected.equals(location.country())||query.length()<2)continue;
   String name=normal(location.name());if(!name.startsWith(query)&&!PlanSearchQuery.completesLocation(term,name)&&intent.geography().stream().noneMatch(g->normal(g.name()).equals(name)))continue;
   String id=location.countryLevel()?"country:"+location.country():"city:"+name+":"+location.country();if(destinations.containsKey(id))continue;
   long count=entries.stream().filter(e->selected.isBlank()||e.parsed().evidence().countries().contains(selected)).filter(e->days==null||e.parsed().plan().dayCount()==days).filter(e->e.parsed().evidence().countries().contains(location.country())&&(location.countryLevel()||TravelConcepts.has(e.parsed().evidence().text(),name))).count();
   destinations.put(id,new Destination(id,location.countryLevel()?COUNTRIES.get(location.country()):location.name(),location.country(),location.countryLevel()?"country":"city",count));
  }
  String revision=digest(today+"|"+filters.signature()+"|"+entries.stream().map(e->e.row().getId()+":"+e.row().getRevision()+":"+digest(e.row().getSnapshot())+(!trendingCollection&&(sort.equals("relevance")||sort.equals("recent"))?"":":"+e.row().getViewCount()+":"+e.row().getRatingCount()+":"+e.row().getRatingSum()+":"+e.row().getCommentCount()+":"+allCopies.getOrDefault(e.row().getId(),SharedPlanCopyRepository.CopyStats.ZERO).copies()+":"+activity.getOrDefault(e.row().getId(),0L))).sorted().collect(java.util.stream.Collectors.joining("|")));
  String fingerprint=digest(query+"|"+country+"|"+days+"|"+sort+"|"+filters.signature()+"|"+TravelConcepts.DATA.version());
  String interpretation=intent.required().isEmpty()?"":String.join(" and ",intent.required().stream().map(g->String.join(" or ",g.stream().sorted().map(TravelConcepts::label).toList())).toList());
  var assist=new SearchAssist(1,TravelConcepts.DATA.version(),term,country,days,sort,fingerprint,revision,256,interpretation,intent.notices(),suggestions,intent.blocked());
  var querySuggestions=suggestions.stream().filter(s->s.kind().equals("rewrite")).limit(3).map(s->new QuerySuggestion(s.text(),"vocabulary")).toList();
  String correction=PlanSearchQuery.parse(term,locations.values(),country).correction;
  boolean strongLast=(long)(page+1)*size>=matching.size(),weakLast=(long)(page+1)*size>=relevant.size();
  org.slf4j.LoggerFactory.getLogger(SmartPlanSearchEngine.class).debug("Public search completed: publications={}, elapsedMs={}",entries.size(),(System.nanoTime()-began)/1_000_000);
  return new SearchPage(window(matching,recommended+page*size,size).stream().map(summary).toList(),page==0?window(matching,0,recommended).stream().map(summary).toList():List.of(),destinations.values().stream().sorted(Comparator.comparingLong(Destination::count).reversed().thenComparing(Destination::label)).limit(6).toList(),window(matching,0,4).stream().map(summary).toList(),page,(long)(page+1)*size>=matching.size()-recommended,matching.size()-recommended,matching.size(),strongPage,weakPage,matching.size(),relevant.size(),strongLast,weakLast,window(relevant,0,4).stream().map(summary).toList(),Map.copyOf(reasons),correction,querySuggestions,intent.notices().isEmpty()?null:String.join(" ",intent.notices()),assist,Map.copyOf(discoveryLabels));
 }
 private static <T> List<T> list(List<T> value){return value==null?List.of():value;}
}
