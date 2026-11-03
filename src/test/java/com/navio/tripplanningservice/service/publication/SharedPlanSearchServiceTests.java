package com.navio.tripplanningservice.service.publication;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.navio.tripplanningservice.repository.ExploreSearchRepository;
import com.navio.tripplanningservice.repository.SharedPlanCopyRepository;
import com.navio.tripplanningservice.model.TripPublication;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.*;
import java.util.stream.Stream;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class SharedPlanSearchServiceTests {
 final ExploreSearchRepository repository=mock(ExploreSearchRepository.class);
 final SharedPlanCopyRepository copies=mock(SharedPlanCopyRepository.class);
 final SharedPlanSearchService service=new SharedPlanSearchService(repository,new ObjectMapper().findAndRegisterModules(),new ExplorePlanSummarizer(),copies);
 TripPublication row(String token,String title,String city,String country,int days){
  return TripPublication.builder().id(UUID.randomUUID()).token(token).status(TripPublication.PublicationStatus.ACTIVE).listedInExplore(true)
   .sanitizerVersion(PlanPublicationSanitizer.SANITIZER_VERSION).listedAt(Instant.EPOCH).updatedAt(Instant.EPOCH)
   .snapshot("{\"sanitizerVersion\":"+PlanPublicationSanitizer.SANITIZER_VERSION+",\"title\":\""+title+"\",\"destinationCity\":\""+city+"\",\"destinationCountry\":\""+country+"\",\"dayCount\":"+days+",\"days\":[]}").build();
 }
 void rows(TripPublication... rows){when(repository.streamListed(anyInt())).thenAnswer(i->Arrays.stream(rows));}
 @Test void countryMatchesEvenWhenTitleDoesNotContainItsName(){
  rows(row("a","Quiet weekend","Kyoto","Japan",4));
  var result=service.search("Japan","",null,0,12,"relevance");
  assertThat(result.matchedCount()).isEqualTo(1);assertThat(result.recommendations()).extracting(p->p.token()).containsExactly("a");
 }
 @Test void countryDurationAndTokensAllApply(){
  rows(row("a","Kyoto temples","Kyoto","Japan",4),row("b","Kyoto temples","Kyoto","Japan",5),row("c","Kyoto temples","Kyoto","Thailand",4));
  assertThat(service.search("Kyoto temples","JP",4,0,12,"relevance").matchedCount()).isEqualTo(1);
 }
 @Test void partialTokensAliasesAndAccentsAreSupported(){
  rows(row("a","Ban riverside","Bangkok","Thailand",3));
  assertThat(service.search("ban","",null,0,12,"relevance").matchedCount()).isEqualTo(1);
  rows(row("b","Harbour walk","New York","United States",2));
  assertThat(service.search("USA","US",null,0,12,"relevance").matchedCount()).isEqualTo(1);
  rows(row("c","Café walk","Paris","France",2));
  assertThat(service.search("cafe","",null,0,12,"relevance").matchedCount()).isEqualTo(1);
 }
 @Test void privateRevokedStaleAndUnreadableSnapshotsNeverLeak(){
  var privateRow=row("secret","Secret","Kyoto","Japan",4);privateRow.setListedInExplore(false);
  var stale=row("old","Old","Kyoto","Japan",4);stale.setSanitizerVersion(0);
  var revoked=row("revoked","Revoked","Kyoto","Japan",4);revoked.setStatus(TripPublication.PublicationStatus.REVOKED);
  var broken=row("broken","Broken","Kyoto","Japan",4);broken.setSnapshot("not json");
  rows(privateRow,stale,revoked,broken);
  var result=service.search("Japan","",null,0,12,"relevance");assertThat(result.matchedCount()).isZero();assertThat(result.plans()).isEmpty();
  verifyNoInteractions(copies);
 }
 @Test void scansBeyondFirstPageAndNeverDuplicatesRecommendations(){
  var values=new ArrayList<TripPublication>();for(int i=0;i<35;i++)values.add(row(String.format("%02d",i),"Japan temples "+i,"Kyoto","Japan",4));
  rows(values.toArray(TripPublication[]::new));var first=service.search("Japan","",null,0,12,"relevance");
  var second=service.search("Japan","",null,1,12,"relevance");
  assertThat(first.matchedCount()).isEqualTo(35);assertThat(first.totalElements()).isEqualTo(29);
  assertThat(second.content()).hasSize(12);assertThat(second.recommendations()).isEmpty();
  assertThat(first.content()).doesNotContainAnyElementsOf(first.recommendations());
  assertThat(second.content()).doesNotContainAnyElementsOf(first.content());
 }
 @Test void suggestionsAreRealAndDoNotReadPrivateNotes(){
  rows(row("a","Bangkok Trip","Bangkok","Thailand",3));var result=service.search("ban","",null,0,12,"relevance");
  assertThat(result.destinations()).anyMatch(d->d.label().equals("Bangkok"));assertThat(result.plans()).extracting(p->p.title()).contains("Bangkok Trip");
 }

 TripPublication withPlaces(TripPublication value,String type,String name,String address){
  value.setSnapshot(value.getSnapshot().replace("\"days\":[]","\"days\":[{\"items\":[{\"type\":\"place\",\"placeType\":\""+type+"\",\"name\":\""+name+"\",\"address\":\""+address+"\"}]}]"));return value;
 }
 @Test void contextualQuerySeparatesStrongAndRelatedWithoutIgnoringGeography(){
  rows(withPlaces(row("hua","Coast escape","Hua Hin","Thailand",3),"restaurant","Beach seafood restaurant","Hua Hin, Thailand"),
       withPlaces(row("bangkok","Market weekend","Bangkok","Thailand",3),"restaurant","Food market","Bangkok, Thailand"),
       withPlaces(row("japan","Food tour","Tokyo","Japan",3),"restaurant","Food market","Tokyo, Japan"));
  var result=service.search("Huahin scenary and food trip","",null,0,12,"relevance");
  assertThat(result.matching()).extracting(p->p.token()).containsExactly("hua");
  assertThat(result.related()).isEmpty();
  assertThat(result.correction()).contains("Hua Hin").contains("scenic");
 }
 @Test void relatedTitlesRemainWhenNoStrongPlanMatchesAndFiltersNeverRelax(){
  rows(row("thai","Quiet escape","Hua Hin","Thailand",4),row("other","Quiet escape","Tokyo","Japan",4));
  var result=service.search("Thailand food trip","",null,0,12,"relevance");
  assertThat(result.matching()).isEmpty();assertThat(result.relatedPlans()).isEmpty();
  assertThat(result.relatedTotal()).isZero();assertThat(result.reasons()).isEmpty();
  assertThat(service.search("Thailand food trip","TH",5,0,12,"relevance").related()).isEmpty();
 }
 @Test void regionAndFillerWordsDoNotHideRelevantPlansOrShowUnrelatedCountries(){
  rows(withPlaces(row("ca","Coastal food discoveries","Monterey","United States",4),"restaurant","Seafood restaurant","Monterey, California, USA"),
       withPlaces(row("fr","Food discoveries","Paris","France",4),"restaurant","Seafood restaurant","Paris, France"));
  var result=service.search("California food focus trip","",null,0,12,"relevance");
  assertThat(result.matching()).extracting(p->p.token()).containsExactly("ca");assertThat(result.related()).isEmpty();
  assertThat(service.search("zxqv invented unknown","",null,0,12,"relevance").related()).isEmpty();
 }
 @Test void bothPoolsPaginateWithoutDuplicatesAndSuggestionsDoNotRequireAllWords(){
  var values=new ArrayList<TripPublication>();for(int i=0;i<30;i++)values.add(row("r"+i,"Quiet escape "+i,"Hua Hin","Thailand",4));
  rows(values.toArray(TripPublication[]::new));var first=service.search("Thailand food trip","",null,0,12,"relevance");
  var next=service.search("Thailand food trip","",null,1,12,"relevance");
  assertThat(first.related()).isEmpty();assertThat(next.related()).isEmpty();assertThat(next.related()).isEmpty();
  assertThat(first.relatedLast()).isTrue();assertThat(first.destinations()).anyMatch(d->d.label().equals("Thailand"));
 }

 @Test void sameNameCitiesUseTypedOrSelectedCountryWithoutArbitraryCatalogueOrder(){
  rows(row("us","Paris walks","Paris","United States",4),row("fr","Paris walks","Paris","France",4),row("jp","Food tour","Tokyo","Japan",4));
  assertThat(service.search("Paris France","",null,0,12,"relevance").matching()).extracting(p->p.token()).containsExactly("fr");
  assertThat(service.search("Paris","FR",null,0,12,"relevance").matching()).extracting(p->p.token()).containsExactly("fr");
  assertThat(service.search("Paris","",null,0,12,"relevance").matching()).extracting(p->p.token()).containsExactlyInAnyOrder("fr","us");
  assertThat(service.search("Paris food trip","",null,0,12,"relevance").related()).extracting(p->p.token()).doesNotContain("jp");
 }
 @Test void legacyClientsDoNotPageThroughRelatedOnlyResults(){
  var values=new ArrayList<TripPublication>();for(int i=0;i<30;i++)values.add(row("r"+i,"Quiet escape "+i,"Hua Hin","Thailand",4));
  rows(values.toArray(TripPublication[]::new));var result=service.search("Thailand food trip","",null,0,12,"relevance");
  assertThat(result.last()).isTrue();assertThat(result.matchingLast()).isTrue();assertThat(result.relatedLast()).isTrue();
 }

 @Test void shortCountryAliasesStillAnchorMultiwordQueries(){
  rows(withPlaces(row("uk","Market discoveries","London","United Kingdom",4),"restaurant","Food hall","London, UK"),
       withPlaces(row("jp","Food discoveries","Tokyo","Japan",4),"restaurant","Food hall","Tokyo, Japan"));
  var result=service.search("UK food trip","",null,0,12,"relevance");
  assertThat(result.matching()).extracting(p->p.token()).containsExactly("uk");assertThat(result.related()).isEmpty();
 }

 @Test void wholeCatalogueCompletesMultiwordCityWhenItWasNotInTheLoadedFeed(){
  rows(row("hua","Coast escape","Hua Hin","Thailand",4));
  assertThat(service.search("Thailand Hua Hi","",null,0,12,"relevance").destinations()).anyMatch(d->d.label().equals("Hua Hin"));
 }

 @Test void specificActivitiesRemainSpecificRatherThanOnlyMatchingTheirBroadTheme(){
  rows(withPlaces(row("beach","Coast escape","Hua Hin","Thailand",4),"tourist_attraction","Beach viewpoint","Hua Hin, Thailand"),
       withPlaces(row("mountain","Mountain escape","Hua Hin","Thailand",4),"park","Mountain garden","Hua Hin, Thailand"));
  var result=service.search("Hua Hin beach trip","",null,0,12,"relevance");
  assertThat(result.matching()).extracting(p->p.token()).containsExactly("beach");assertThat(result.related()).isEmpty();
 }
 TripPublication dated(TripPublication row,int day,boolean shared){
  row.setSnapshot(row.getSnapshot().replace("\"days\":[]","\"included\":{\"includeDates\":"+shared+"},\"days\":[{\"date\":\"2026-12-"+String.format("%02d",day)+"\",\"items\":[]}]"));return row;
 }
 @Test void judgedChristmasCatalogueHasNoCountryOnlyFalsePositivesAndRanksEvidence(){
  rows(row("exact","Christmas markets","Bangkok","Thailand",3),dated(row("near","Quiet escape","Bangkok","Thailand",3),25,true),dated(row("early","Quiet escape","Bangkok","Thailand",3),3,true),dated(row("hidden","Coastal escape","Bangkok","Thailand",3),25,false),row("ordinary","Popular coast","Bangkok","Thailand",3),row("wrong","Christmas","Tokyo","Japan",3));
  var result=service.search("Thailand Christmas trip","",null,0,12,"relevance");
  assertThat(result.matching()).extracting(p->p.token()).containsExactly("exact");
  assertThat(result.related()).extracting(p->p.token()).containsExactly("near","early");
  assertThat(result.relatedTotal()).isEqualTo(2);assertThat(result.matchingTotal()).isEqualTo(1);
  assertThat(result.relatedPlans()).extracting(p->p.token()).containsExactly("near","early");
  assertThat(result.reasons().values()).allMatch(r->r.contains("December"));
  assertThat(service.search("Thailand Christmas 2026","",null,0,12,"relevance").notice()).contains("year");
 }
 @Test void temporalRelevantPoolPaginatesWithoutDuplicates(){
  var values=new ArrayList<TripPublication>();for(int i=0;i<30;i++)values.add(dated(row("dec"+i,"Quiet escape","Bangkok","Thailand",3),25,true));
  rows(values.toArray(TripPublication[]::new));
  var first=service.search("Thailand Christmas","TH",3,0,12,"relevance");var next=service.search("Thailand Christmas","TH",3,1,12,"relevance");
  assertThat(first.related()).hasSize(12);assertThat(next.related()).hasSize(12);assertThat(first.relatedLast()).isFalse();
  assertThat(next.related()).doesNotContainAnyElementsOf(first.related());assertThat(first.matching()).isEmpty();
 }

 @Test void publicIntroductionCanSupportActivityButSuggestionCannotDiscardIt(){
  var plan=row("intro","City break","Bangkok","Thailand",3);
  plan.setSnapshot(plan.getSnapshot().replace("\"days\":[]","\"startingPart\":{\"title\":\"Before you go\",\"content\":\"A food itinerary visiting sushi restaurants.\"},\"days\":[]"));
  rows(plan);
  var result=service.search("Thailand food","",null,0,12,"relevance");
  assertThat(result.matching()).extracting(p->p.token()).containsExactly("intro");
  assertThat(result.querySuggestions()).noneMatch(p->p.text().equals("City break"));
 }
 @Test void speculativeFoodNotesDoNotEstablishActivity(){
  var plan=row("intro","City break","Bangkok","Thailand",3);
  plan.setSnapshot(plan.getSnapshot().replace("\"days\":[]","\"startingPart\":{\"title\":\"Before you go\",\"content\":\"Maybe consider food stops.\"},\"days\":[]"));rows(plan);
  assertThat(service.search("Thailand food","",null,0,12,"relevance").matching()).isEmpty();
 }

 @Test void cataloguePhraseMustPreserveTheTypedActivity(){
  rows(withPlaces(row("title","City break","Bangkok","Thailand",3),"restaurant","Sushi restaurant","Bangkok, Thailand"));
  var result=service.search("Thailand sushi","",null,0,12,"relevance");
  assertThat(result.matching()).hasSize(1);assertThat(result.querySuggestions()).noneMatch(p->p.text().equals("City break"));
 }

 @Test void ownerQuestionsDoNotClaimAffirmativeActivitiesOrEvents(){
  for(String question:List.of("Visit the Christmas market?","Should we visit a sushi restaurant?")){
   var plan=row("question","City break","Bangkok","Thailand",3);
   plan.setSnapshot(plan.getSnapshot().replace("\"days\":[]","\"startingPart\":{\"title\":\"Ideas\",\"content\":\""+question+"\"},\"days\":[]"));rows(plan);
   assertThat(service.search(question.contains("Christmas")?"Thailand Christmas":"Thailand food","",null,0,12,"relevance").matching()).isEmpty();
  }
 }

 @Test void thousandPlanCatalogueKeepsResultWindowsBoundedAndCountsAccurate(){
  var values=new ArrayList<TripPublication>();for(int i=0;i<1000;i++)values.add(dated(row("load"+i,"Quiet escape","Bangkok","Thailand",3),25,true));
  rows(values.toArray(TripPublication[]::new));
  var elapsed=new ArrayList<Long>();
  for(int run=0;run<3;run++){
   long before=System.nanoTime();var result=service.search("Thailand Christmas","TH",3,0,12,"relevance");elapsed.add((System.nanoTime()-before)/1_000_000);
   assertThat(result.relatedTotal()).isEqualTo(1000);assertThat(result.related()).hasSize(12);assertThat(result.relatedPlans()).hasSize(4);assertThat(result.matching()).isEmpty();
  }
  System.out.println("Search benchmark: 1000 public plans, three requests in ms="+elapsed);
 }

}
