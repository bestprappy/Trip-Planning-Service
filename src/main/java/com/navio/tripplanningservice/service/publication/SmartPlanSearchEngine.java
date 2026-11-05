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
  term=Objects.toString(term,"").strip();country=Objects.toString(country,"").toUpperCase(Locale.ROOT);
  if(term.codePointCount(0,term.length())>256||!country.isEmpty()&&!COUNTRIES.containsKey(country)||days!=null&&(days<1||days>36500)||page<0||page>500||size<1||size>48||!Set.of("relevance","recent","trending").contains(sort))throw new IllegalArgumentException("Invalid search parameters");
  long began=System.nanoTime();var entries=new ArrayList<Entry>();
  try(Stream<TripPublication> stream=repository.streamListed(PlanPublicationSanitizer.SANITIZER_VERSION)){stream.forEach(row->{var parsed=parse(row);if(parsed!=null)entries.add(new Entry(row,parsed));});}
  var locations=new LinkedHashMap<String,PlanSearchQuery.Location>();COUNTRIES.forEach((code,name)->locations.put("country:"+code,new PlanSearchQuery.Location(name,code,true)));
  String normalizedTerm=normal(term);
  CODES.forEach((alias,code)->{if(alias.length()>2||Set.of("uk","us").contains(alias)||normalizedTerm.equals(alias))locations.putIfAbsent("alias:"+alias,new PlanSearchQuery.Location(alias,code,true));});
  locations.put("california",new PlanSearchQuery.Location("California","US",false));locations.put("hua-hin",new PlanSearchQuery.Location("Hua Hin","TH",false));
  for(var entry:entries){var plan=entry.parsed().plan();String c=code(plan.destinationCountry());if(!c.isBlank()&&plan.destinationCity()!=null)locations.putIfAbsent(normal(plan.destinationCity())+":"+c,new PlanSearchQuery.Location(plan.destinationCity(),c,false));
   for(var day:Stream.concat(list(plan.days()).stream(),list(plan.lists()).stream()).filter(Objects::nonNull).toList())for(var item:list(day.items()))if(item!=null&&item.city()!=null){String placeCountry=c;if(item.address()!=null){String[] parts=item.address().split(",");String found=code(parts[parts.length-1]);if(!found.isBlank())placeCountry=found;}if(!placeCountry.isBlank())locations.putIfAbsent(normal(item.city())+":"+placeCountry,new PlanSearchQuery.Location(item.city(),placeCountry,false));}}
  String selected=country;var intent=SearchIntentParser.parse(term,locations.values(),country,days);
  Comparator<Ranked> order=Comparator.comparingInt((Ranked r)->"relevance".equals(sort)?r.match().score():0).reversed();
  if(sort.equals("trending"))order=order.thenComparing(Comparator.comparingLong((Ranked r)->Objects.requireNonNullElse(r.entry().row().getViewCount(),0L)).reversed());
  order=order.thenComparing(r->r.entry().row().getListedAt(),Comparator.nullsLast(Comparator.reverseOrder())).thenComparing(r->r.entry().row().getToken());
  var matching=new ArrayList<Ranked>();var relevant=new ArrayList<Ranked>();
  for(var entry:entries){if(!selected.isBlank()&&!entry.parsed().evidence().countries().contains(selected))continue;var match=PlanSearchEligibility.evaluate(intent,entry.parsed().evidence(),entry.parsed().plan().dayCount());if(match!=null)(match.strong()?matching:relevant).add(new Ranked(entry,match));}
  matching.sort(order);relevant.sort(order);
  var suggestions=QuerySuggestionComposer.compose(intent,locations.values(),selected,days,candidate->entries.stream().filter(e->selected.isBlank()||e.parsed().evidence().countries().contains(selected)).filter(e->PlanSearchEligibility.evaluate(candidate,e.parsed().evidence(),e.parsed().plan().dayCount())!=null).count());
  var retained=new LinkedHashMap<UUID,Ranked>();int capacity=page*size+size+6;
  Stream.concat(window(matching,0,capacity).stream(),window(relevant,0,capacity).stream()).forEach(r->retained.put(r.entry().row().getId(),r));
  var copyStats=retained.isEmpty()?Map.<UUID,SharedPlanCopyRepository.CopyStats>of():copies.counts(new ArrayList<>(retained.keySet()));
  Function<Ranked,ExplorePlanSummary> summary=r->{var e=r.entry();var p=e.parsed().plan();var row=e.row();return summarizer.summarize(row.getToken(),p,row.getAuthorDisplayName(),row.getListedAt(),row.getUpdatedAt(),Objects.requireNonNullElse(row.getViewCount(),0L),copyStats.getOrDefault(row.getId(),SharedPlanCopyRepository.CopyStats.ZERO).copies()).withFeedback(row);};
  int recommended=!term.isBlank()&&sort.equals("relevance")?Math.min(6,matching.size()):0;
  List<ExplorePlanSummary> strongPage=window(matching,page*size,size).stream().map(summary).toList(),weakPage=window(relevant,page*size,size).stream().map(summary).toList();
  var reasons=new LinkedHashMap<String,String>();Stream.concat(window(relevant,page*size,size).stream(),window(relevant,0,4).stream()).forEach(r->reasons.put(r.entry().row().getToken(),r.match().reason()));
  var destinations=new LinkedHashMap<String,Destination>();String query=normal(term);
  for(var location:locations.values()){
   if(location.name().length()<3||!selected.isBlank()&&!selected.equals(location.country())||query.length()<2)continue;
   String name=normal(location.name());if(!name.startsWith(query)&&!PlanSearchQuery.completesLocation(term,name)&&intent.geography().stream().noneMatch(g->normal(g.name()).equals(name)))continue;
   String id=location.countryLevel()?"country:"+location.country():"city:"+name+":"+location.country();if(destinations.containsKey(id))continue;
   long count=entries.stream().filter(e->selected.isBlank()||e.parsed().evidence().countries().contains(selected)).filter(e->days==null||e.parsed().plan().dayCount()==days).filter(e->e.parsed().evidence().countries().contains(location.country())&&(location.countryLevel()||TravelConcepts.has(e.parsed().evidence().text(),name))).count();
   destinations.put(id,new Destination(id,location.countryLevel()?COUNTRIES.get(location.country()):location.name(),location.country(),location.countryLevel()?"country":"city",count));
  }
  String revision=digest(entries.stream().map(e->e.row().getId()+":"+e.row().getRevision()+":"+digest(e.row().getSnapshot())).sorted().collect(java.util.stream.Collectors.joining("|")));
  String fingerprint=digest(query+"|"+country+"|"+days+"|"+sort+"|"+TravelConcepts.DATA.version());
  String interpretation=intent.required().isEmpty()?"":String.join(" and ",intent.required().stream().map(g->String.join(" or ",g.stream().sorted().map(TravelConcepts::label).toList())).toList());
  var assist=new SearchAssist(1,TravelConcepts.DATA.version(),term,country,days,sort,fingerprint,revision,256,interpretation,intent.notices(),suggestions,intent.blocked());
  var querySuggestions=suggestions.stream().filter(s->s.kind().equals("rewrite")).limit(3).map(s->new QuerySuggestion(s.text(),"vocabulary")).toList();
  String correction=PlanSearchQuery.parse(term,locations.values(),country).correction;
  boolean strongLast=(long)(page+1)*size>=matching.size(),weakLast=(long)(page+1)*size>=relevant.size();
  org.slf4j.LoggerFactory.getLogger(SmartPlanSearchEngine.class).debug("Public search completed: publications={}, elapsedMs={}",entries.size(),(System.nanoTime()-began)/1_000_000);
  return new SearchPage(window(matching,recommended+page*size,size).stream().map(summary).toList(),page==0?window(matching,0,recommended).stream().map(summary).toList():List.of(),destinations.values().stream().sorted(Comparator.comparingLong(Destination::count).reversed().thenComparing(Destination::label)).limit(6).toList(),window(matching,0,4).stream().map(summary).toList(),page,(long)(page+1)*size>=matching.size()-recommended,matching.size()-recommended,matching.size(),strongPage,weakPage,matching.size(),relevant.size(),strongLast,weakLast,window(relevant,0,4).stream().map(summary).toList(),Map.copyOf(reasons),correction,querySuggestions,intent.notices().isEmpty()?null:String.join(" ",intent.notices()),assist);
 }
 private static <T> List<T> list(List<T> value){return value==null?List.of():value;}
}
