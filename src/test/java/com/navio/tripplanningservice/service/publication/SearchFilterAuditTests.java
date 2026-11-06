package com.navio.tripplanningservice.service.publication;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.navio.tripplanningservice.model.TripPublication;
import org.junit.jupiter.api.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.stream.*;
import static org.assertj.core.api.Assertions.*;

/** Offline audit: real parsing/ranking/composition; only the public-catalogue repository is mocked.
 * No provider/client is constructed and no production publication is created. */
class SearchFilterAuditTests extends SearchTestFixtures {
 record Plan(String id,String city,String country,int days,String title,String type,String stop,String date) {}
 record Query(String text,String country,String expectedToken) {}
 record Audit(String query,String country,Integer days,String sort,String collections,String themes,
              List<String> matching,List<String> relevant,List<SearchAssist.Suggestion> suggestions,
              List<String> notices,String assessment) {}
 static final List<Audit> REPORT=new ArrayList<>();
 static final Map<String,SharedPlanSearchService.SearchPage> RESPONSES=new LinkedHashMap<>();
 static final List<Plan> DATA=List.of(
  new Plan("th-beach","Hua Hin","Thailand",4,"Thailand beach holiday with seafood","beach","Seaside beach and seafood restaurant","2026-07-12"),
  new Plan("th-christmas","Bangkok","Thailand",3,"Bangkok Christmas festival weekend","festival","Festive market","2026-12-24"),
  new Plan("th-december","Krabi","Thailand",4,"Krabi coastal escape","beach","Railay Beach","2026-12-20"),
  new Plan("jp-winter","Tokyo","Japan",4,"Tokyo winter city trip","museum","Art museum","2026-02-10"),
  new Plan("jp-okinawa","Okinawa","Japan",5,"Okinawa summer food and beach holiday","restaurant","Seafood restaurant and beach","2026-07-12"),
  new Plan("gb-art","London","United Kingdom",4,"London art and museum discovery","museum","Art gallery","2026-05-20"),
  new Plan("us-night","New York","United States",3,"New York night city skyline","viewpoint","Night city rooftop viewpoint","2026-10-10"),
  new Plan("fr-spring","Paris","France",5,"Paris spring gardens and art","garden","Botanical garden and museum","2026-05-10"),
  new Plan("ch-hike","Lucerne","Switzerland",5,"Lucerne spring mountains and hiking","national_park","Mountain forest hiking trail","2026-05-12"),
  new Plan("th-ev","Bangkok","Thailand",2,"Bangkok EV weekend road trip","charger","EV charging station","2026-11-10"),
  new Plan("jp-food","Tokyo","Japan",4,"Tokyo food discoveries","restaurant","Sushi restaurant","2026-04-14"),
  new Plan("gb-short","London","United Kingdom",2,"London museum weekend","museum","History museum","2026-11-15"),
  new Plan("th-loy","Chiang Mai","Thailand",3,"Loy Krathong festival trip","festival","Riverside festival","2026-11-24")
 );
 static final List<Query> QUERIES=List.of(
  new Query("Thailand Beach Trip Holliday","TH",""),
  new Query("Tokyo Winter Trip","JP","jp-winter"),
  new Query("Okonawa Summer Trip food focus","JP",""),
  new Query("UK Trip for art and musuem","GB",""),
  new Query("US trip for night city","US","us-night"),
  new Query("Interesting trip in Europe during this Spring","FR",""),
  new Query("Western country trip with hiking","CH",""),
  new Query("Thailand","TH","th-beach"),
  new Query("Jap","JP",""),
  new Query("Japan food","JP","jp-food"),
  new Query("Bangkok EV weekend","TH","th-ev"),
  new Query("Thailand Christmas holiday","TH","th-christmas"),
  new Query("Thailand December beach","TH","th-december"),
  new Query("Tokyo sushi","JP","jp-food"),
  new Query("Okinawa summer food","JP","jp-okinawa"),
  new Query("UK museums","GB","gb-art"),
  new Query("US night city","US","us-night"),
  new Query("France spring gardens","FR","fr-spring"),
  new Query("Switzerland hiking","CH","ch-hike"),
  new Query("Thailand food and beaches","TH","th-beach"),
  new Query("Thailand food or museums","TH","th-beach"),
  new Query("Japan food without museums","JP","jp-food"),
  new Query("Japan nature like gardens, forests or mountains","JP",""),
  new Query("4 day Tokyo food trip","JP","jp-food"),
  new Query("Tokyo food prefer sushi","JP","jp-food"),
  new Query("Thailand \"Railay Beach\"","TH","th-december"),
  new Query("Japan Christmas festival","JP",""),
  new Query("Atlantis underwater spaceship holiday","TH",""),
  new Query("Thailand beach without seafood","TH","th-december"),
  new Query("UK or France museum trip","GB","gb-art"),
  new Query("Japan winter 2027","JP",""),
  new Query("","TH","th-beach")
 );
 List<TripPublication> catalogue(){
  return DATA.stream().map(p->{
   var row=row(p.id(),p.title(),p.city(),p.country(),p.days());
   try {
    var root=(com.fasterxml.jackson.databind.node.ObjectNode)new ObjectMapper().readTree(row.getSnapshot());
    root.putObject("included").put("includeDates",true).put("includeNotes",true);
    var day=root.putArray("days").addObject();day.put("date",p.date());
    var item=day.putArray("items").addObject();item.put("type","place").put("name",p.stop()).put("placeType",p.type()).put("address",p.city()+", "+p.country());
    row.setSnapshot(root.toString());row.setViewCount((long)p.days());row.setListedAt(Instant.parse("2026-10-01T00:00:00Z").plusSeconds(p.days()));
    return row;
   } catch(Exception e){throw new IllegalStateException(e);}
  }).toList();
 }
 @BeforeEach void fixedDate(){service.discoveryClock=Clock.fixed(Instant.parse("2026-11-07T12:00:00Z"),ZoneOffset.UTC);}
 @TestFactory Stream<DynamicTest> queryCountryDurationMatrix(){
  var catalogue=catalogue();var byId=DATA.stream().collect(Collectors.toMap(Plan::id,p->p));
  return QUERIES.stream().flatMap(q->IntStream.range(0,4).mapToObj(mode->DynamicTest.dynamicTest(q.text()+" / country="+(mode%2==1?q.country():"Any")+" / days="+(mode>=2?4:"Any"),()->{
   rows(catalogue.toArray(TripPublication[]::new));String country=mode%2==1?q.country():"";Integer days=mode>=2?4:null;
   var r=service.search(q.text(),country,days,0,48,"relevance","","");
   RESPONSES.put(q.text()+"|"+country+"|"+Objects.toString(days,"")+"|relevance||",r);
   check(r,q.text(),country,days,"relevance","","");
   var ids=Stream.concat(r.matching().stream(),r.related().stream()).map(p->p.token()).toList();
   boolean season=q.text().toLowerCase(Locale.ROOT).matches(".*\\b(?:winter|summer|spring|autumn)\\b.*");
   // The existing approved parser explicitly blocks unverified seasonal conditions.
   // Record this as a capability gap, not a passed seasonal-retrieval assertion.
   if(season){assertThat(r.searchAssist().blocked()).isTrue();assertThat(ids).isEmpty();}
   else if(!q.expectedToken().isBlank()&&(days==null||byId.get(q.expectedToken()).days()==days))assertThat(ids).as("Known supported query retrieves independently specified fixture").contains(q.expectedToken());
   REPORT.add(audit(r,q.text(),country,days,"relevance","","",season?"CAPABILITY GAP: season words are explicitly blocked; use a month. No seasonal retrieval success claimed.":ids.isEmpty()?"No results: inspect wording or lack of qualifying evidence":"Retrieved qualified test publications"));
  })));
 }
 void check(SharedPlanSearchService.SearchPage r,String query,String country,Integer days,String sort,String collections,String themes){
  var all=Stream.concat(r.matching().stream(),r.related().stream()).toList();
  assertThat(all.stream().map(p->p.token()).toList()).doesNotHaveDuplicates();
  if(!country.isEmpty())assertThat(all).allMatch(p->SharedPlanSearchService.code(p.destinationCountry()).equals(country));
  if(days!=null)assertThat(all).allMatch(p->p.dayCount()==days);
  assertThat(r.searchAssist().query()).isEqualTo(query.strip());assertThat(r.searchAssist().country()).isEqualTo(country);assertThat(r.searchAssist().days()).isEqualTo(days);assertThat(r.searchAssist().sort()).isEqualTo(sort);
  assertThat(r.searchAssist().suggestions().stream().map(s->s.text()).toList()).doesNotHaveDuplicates();
  for(var suggestion:r.searchAssist().suggestions()){
   assertThat(suggestion.action().query()).isEqualTo(suggestion.text());assertThat(suggestion.text().length()).isLessThanOrEqualTo(256);
   var next=service.search(suggestion.action().query(),country,days,0,48,sort,collections,themes);
   if(suggestion.availability().equals("available"))assertThat(next.matchingTotal()+next.relatedTotal()).isPositive();
   if(suggestion.availability().equals("none"))assertThat(next.matchingTotal()+next.relatedTotal()).isZero();
   if(suggestion.kind().equals("rewrite")){
    assertThat(next.matching().stream().map(p->p.token()).toList()).containsExactlyInAnyOrderElementsOf(r.matching().stream().map(p->p.token()).toList());
    assertThat(next.related().stream().map(p->p.token()).toList()).containsExactlyInAnyOrderElementsOf(r.related().stream().map(p->p.token()).toList());
   }
  }
 }
 @TestFactory Stream<DynamicTest> emptyCatalogueStillReturnsHonestAssistance(){
  return QUERIES.stream().map(q->DynamicTest.dynamicTest("empty / "+q.text(),()->{
   rows();var r=service.search(q.text(),"",null,0,48,"relevance","","");check(r,q.text(),"",null,"relevance","","");
   assertThat(r.matchingTotal()+r.relatedTotal()).isZero();assertThat(r.plans()).isEmpty();assertThat(r.relatedPlans()).isEmpty();
   assertThat(r.searchAssist().suggestions()).noneMatch(s->s.availability().equals("available"));
   REPORT.add(audit(r,q.text(),"",null,"relevance","","","Empty catalogue: no invented plans or available suggestions"));
  }));
 }
 @TestFactory Stream<DynamicTest> combinedSortCollectionThemeFilters(){
  return Stream.of("relevance","recent","trending","popular","views","rating","discussed","rating,views","views,recent","recent,rating,views").flatMap(sort->Stream.of("","seasonal","events","seasonal,events").flatMap(collection->Stream.of("","food","nature,culture","ev").map(theme->DynamicTest.dynamicTest(sort+" / "+collection+" / "+theme,()->{
   rows(catalogue().toArray(TripPublication[]::new));var r=service.search("Thailand","TH",null,0,48,sort,collection,theme);check(r,"Thailand","TH",null,sort,collection,theme);
   var filters=PlanDiscovery.Filters.parse(collection,theme);
   var byId=catalogue().stream().collect(Collectors.toMap(TripPublication::getToken,p->p));
   assertThat(Stream.concat(r.matching().stream(),r.related().stream())).allMatch(p->filters.accepts(PublicSearchEvidenceBuilder.build(service.read(byId.get(p.token())),SharedPlanSearchService::code),service.discoveryToday()));
   REPORT.add(audit(r,"Thailand","TH",null,sort,collection,theme,"Filter membership and suggestion availability checked"));
  }))));
 }
 @Test void contradictoryCountryAndDurationFailClosed(){
  rows(catalogue().toArray(TripPublication[]::new));
  for(var q:List.of("Tokyo winter","Japan sushi","4 day Thailand beach")){
   var r=service.search(q,"GB",3,0,48,"views,rating","","");assertThat(r.matchingTotal()+r.relatedTotal()).isZero();assertThat(r.searchAssist().blocked()).isTrue();
  }
 }
 static Audit audit(SharedPlanSearchService.SearchPage r,String q,String c,Integer d,String s,String co,String th,String assessment){return new Audit(q,c,d,s,co,th,r.matching().stream().map(p->p.token()).toList(),r.related().stream().map(p->p.token()).toList(),r.searchAssist().suggestions(),r.searchAssist().notices(),assessment);}
 @AfterAll static void saveReport() throws Exception {var json=new ObjectMapper().findAndRegisterModules().disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS).writerWithDefaultPrettyPrinter();Files.writeString(Path.of("target/search-filter-audit.json"),json.writeValueAsString(REPORT));Files.writeString(Path.of("target/search-filter-browser-responses.json"),json.writeValueAsString(RESPONSES));System.out.println("OFFLINE_SEARCH_FILTER_AUDIT cases="+REPORT.size()+" Google calls=0 (no provider clients constructed)");}
}
