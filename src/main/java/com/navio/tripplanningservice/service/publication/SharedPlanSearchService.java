package com.navio.tripplanningservice.service.publication;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.navio.tripplanningservice.dto.publication.*;
import com.navio.tripplanningservice.model.TripPublication;
import com.navio.tripplanningservice.repository.ExploreSearchRepository;
import com.navio.tripplanningservice.repository.SharedPlanCopyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.text.Normalizer;
import java.util.*;
import java.util.stream.Stream;

/** Streams the eligible public catalogue and retains a bounded ranked window. No stale-content
 * cache, private trip joins, mutations or view increments. Indexing can be added if scale requires. */
@Service @RequiredArgsConstructor @Transactional(readOnly=true)
public class SharedPlanSearchService {
 private final ExploreSearchRepository repository;
 private final ObjectMapper mapper;
 private final ExplorePlanSummarizer summarizer;
 private final SharedPlanCopyRepository copies;
 private static final Map<String,String> COUNTRIES=new TreeMap<>(),CODES=new HashMap<>();
 static {
  for(String code:Locale.getISOCountries()) {String name=Locale.of("",code).getDisplayCountry(Locale.ENGLISH);COUNTRIES.put(code,name);CODES.put(normal(name),code);CODES.put(normal(code),code);}
  for(String alias:List.of("usa","u s","u s a","united states of america"))CODES.put(alias,"US");
  for(String alias:List.of("uk","u k","great britain","britain"))CODES.put(alias,"GB");
  CODES.put("turkiye","TR");CODES.put("south korea","KR");CODES.put("north korea","KP");
 }
 public record Destination(String id,String label,String country,String kind,long count) {}
 public record SearchPage(List<ExplorePlanSummary> content,List<ExplorePlanSummary> recommendations,List<Destination> destinations,
  List<ExplorePlanSummary> plans,int number,boolean last,long totalElements,long matchedCount) {}
 private record Candidate(TripPublication row,PublicPlanSnapshot plan,int score) {}
 public static String normal(String value){return Normalizer.normalize(value==null?"":value,Normalizer.Form.NFD).replaceAll("\\p{M}+","").toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]+"," ").trim().replaceAll(" +"," ");}
 private static String code(String value){return CODES.getOrDefault(normal(value),"");}
 private static String canonical(String value){String c=code(value);return c.isEmpty()?normal(value):normal(COUNTRIES.get(c));}
 private static <T> List<T> safe(List<T> values){return values==null?List.of():values;}
 public SearchPage search(String term,String country,Integer days,int page,int size,String sort){
  term=term==null?"":term.strip();country=country==null?"":country.toUpperCase(Locale.ROOT);
  if(term.length()>100||(!country.isEmpty()&&!COUNTRIES.containsKey(country))||days!=null&&(days<1||days>36500)||page<0||page>500||size<1||size>48||!Set.of("relevance","recent","trending").contains(sort))throw new IllegalArgumentException("Invalid search parameters");
  final String query=canonical(term),selected=country;
  final String[] tokens=query.isBlank()?new String[0]:query.split(" ");
  Comparator<Candidate> order=Comparator.comparingInt((Candidate c)->"relevance".equals(sort)?c.score():0).reversed();
  if("trending".equals(sort))order=order.thenComparing(Comparator.comparingLong((Candidate c)->Optional.ofNullable(c.row().getViewCount()).orElse(0L)).reversed());
  order=order.thenComparing(c->c.row().getListedAt(),Comparator.nullsLast(Comparator.reverseOrder())).thenComparing(c->c.row().getToken());
  int capacity=page*size+size+6;PriorityQueue<Candidate> top=new PriorityQueue<>(order.reversed());
  Map<String,Destination> locations=new HashMap<>();long matched=0;
  try(Stream<TripPublication> stream=repository.streamListed(PlanPublicationSanitizer.SANITIZER_VERSION)){
   var it=stream.iterator();while(it.hasNext()){
    TripPublication row=it.next();if(!row.isListed()||row.getSanitizerVersion()==null||row.getSanitizerVersion()<PlanPublicationSanitizer.SANITIZER_VERSION)continue;
    PublicPlanSnapshot plan;try{plan=mapper.readValue(row.getSnapshot(),PublicPlanSnapshot.class);}catch(Exception unreadable){continue;}
    if(plan==null||plan.sanitizerVersion()<PlanPublicationSanitizer.SANITIZER_VERSION)continue;
    Set<String> countries=new HashSet<>();String primary=code(plan.destinationCountry());if(!primary.isEmpty())countries.add(primary);
    StringBuilder text=new StringBuilder().append(normal(plan.title())).append(' ').append(normal(plan.destinationCity())).append(' ').append(canonical(plan.destinationCountry()));
    for(PublicDayDto day:Stream.concat(safe(plan.days()).stream(),safe(plan.lists()).stream()).toList()){
     if(day==null)continue;for(PublicItemDto item:safe(day.items())){
      if(item==null||!Set.of("place","charger","section").contains(item.type()==null?"":item.type()))continue;
      text.append(' ').append(normal(item.name())).append(' ').append(normal(item.city()));
      if(item.address()!=null){text.append(' ').append(normal(item.address()));String[] parts=item.address().split(",");String c=parts.length==0?"":code(parts[parts.length-1]);if(!c.isEmpty())countries.add(c);}
     }
    }
    if(!selected.isEmpty()&&!countries.contains(selected)||days!=null&&plan.dayCount()!=days)continue;
    String all=text.toString();boolean matches=true;for(String token:tokens)if(!all.contains(token)){matches=false;break;}if(!matches)continue;
    matched++;String title=normal(plan.title()),city=normal(plan.destinationCity());
    int score=tokens.length==0?0:title.equals(query)?140:canonical(plan.destinationCountry()).equals(query)||city.equals(query)?120:title.startsWith(query)?100:title.contains(query)?85:city.startsWith(query)?80:40;
    Candidate candidate=new Candidate(row,plan,score);if(top.size()<capacity)top.add(candidate);else if(order.compare(candidate,top.peek())<0){top.poll();top.add(candidate);}
    if(query.length()>=2&&city.contains(query)&&plan.destinationCity()!=null){String id="city:"+city+":"+primary;Destination old=locations.get(id);locations.put(id,new Destination(id,plan.destinationCity(),primary,"city",old==null?1:old.count()+1));}
    for(String c:countries){String name=COUNTRIES.get(c);if(query.length()>=2&&normal(name).contains(query)){String id="country:"+c;Destination old=locations.get(id);locations.put(id,new Destination(id,name,c,"country",old==null?1:old.count()+1));}}
   }
  }
  List<Candidate> ranked=top.stream().sorted(order).toList();int recommended=0;
  if(!query.isBlank()&&"relevance".equals(sort))while(recommended<Math.min(6,ranked.size())&&ranked.get(recommended).score()>=80)recommended++;
  int from=Math.min(ranked.size(),recommended+page*size),to=Math.min(ranked.size(),from+size);
  var counts=ranked.isEmpty()?Map.<UUID,SharedPlanCopyRepository.CopyStats>of():copies.counts(ranked.stream().map(c->c.row().getId()).filter(Objects::nonNull).toList());
  List<ExplorePlanSummary> summaries=ranked.stream().map(c->summarizer.summarize(c.row().getToken(),c.plan(),c.row().getAuthorDisplayName(),c.row().getListedAt(),c.row().getUpdatedAt(),Optional.ofNullable(c.row().getViewCount()).orElse(0L),counts.getOrDefault(c.row().getId(),SharedPlanCopyRepository.CopyStats.ZERO).copies())).toList();
  if(query.length()>=2)COUNTRIES.forEach((c,name)->{if((selected.isEmpty()||selected.equals(c))&&normal(name).startsWith(query))locations.putIfAbsent("country:"+c,new Destination("country:"+c,name,c,"country",0));});
  var destinations=locations.values().stream().sorted(Comparator.comparingLong(Destination::count).reversed().thenComparing(Destination::label)).limit(4).toList();long remaining=matched-recommended;
  return new SearchPage(List.copyOf(summaries.subList(from,to)),page==0?List.copyOf(summaries.subList(0,recommended)):List.of(),destinations,query.length()>=2?List.copyOf(summaries.subList(0,Math.min(4,summaries.size()))):List.of(),page,(long)(page+1)*size>=remaining,remaining,matched);
 }
}
