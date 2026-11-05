package com.navio.tripplanningservice.service.publication;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class SmartSearchAdversarialTests extends SearchTestFixtures {
 @Test void quotedNameIsAnExactPhraseAndNotAnEvent(){
  rows(withPlaces(row("cafe","Cafe visit","Bangkok","Thailand",3),"cafe","Christmas Cafe","Bangkok, Thailand"));
  assertThat(service.search("Thailand \"Christmas Cafe\"","",null,0,12,"relevance").matching()).hasSize(1);
  assertThat(service.search("Thailand \"Cafe Christmas\"","",null,0,12,"relevance").matching()).isEmpty();
 }
 @Test void selectedCountryProvidesContextForNaturalRewrites(){
  rows();assertThat(service.search("Christmas Holiday","TH",null,0,12,"relevance").querySuggestions()).extracting(SharedPlanSearchService.QuerySuggestion::text).contains("Christmas holiday trips in Thailand");
 }
 @Test void booleanActivityOrDoesNotRelaxCityGeography(){
  rows(withPlaces(row("tokyo","Garden afternoon","Tokyo","Japan",3),"botanical_garden","Garden","Tokyo, Japan"),withPlaces(row("kyoto","Garden afternoon","Kyoto","Japan",3),"botanical_garden","Garden","Kyoto, Japan"));
  assertThat(service.search("Japan Tokyo mountains or gardens","",null,0,12,"relevance").matching()).extracting(p->p.token()).containsExactly("tokyo");
 }
 @Test void explicitlyExcludedUnknownActivityCannotBecomePositiveRequirement(){
  rows(row("club","Nightclubs","Bangkok","Thailand",3));
  assertThat(service.search("Thailand without nightclubs","",null,0,12,"relevance").matching()).isEmpty();
 }
 @Test void alternativesCannotDropTypedDuration(){
  rows();var result=service.search("Thailand Christmas Holiday 3 days","",null,0,12,"relevance");
  assertThat(result.searchAssist().suggestions()).allMatch(s->s.action().query().contains("3 day"));
 }
 @Test void emptySpecificEventSearchOffersAnExplicitUsefulBroaderSearch(){
  rows(row("christmas","Christmas markets","Bangkok","Thailand",3));
  var result=service.search("Thailand Christmas weekend","",null,0,12,"relevance");
  assertThat(result.matching()).isEmpty();assertThat(result.related()).isEmpty();
  assertThat(result.searchAssist().suggestions()).anyMatch(s->s.kind().equals("broaden")&&s.availability().equals("available")&&!s.action().query().toLowerCase().contains("weekend")&&s.changedFacets().contains("interests"));
 }
 @Test void countryContextDoesNotSatisfyAnOrGroupOfCities(){
  rows(row("tokyo","City visit","Tokyo","Japan",3),row("kyoto","City visit","Kyoto","Japan",3),row("osaka","City visit","Osaka","Japan",3));
  assertThat(service.search("Japan Tokyo or Kyoto","",null,0,12,"relevance").matching()).extracting(p->p.token()).containsExactlyInAnyOrder("tokyo","kyoto");
 }
 @Test void quotedExclusionsAndNegatedEventsNeverReverseMeaning(){
  rows(withPlaces(row("beach","Christmas beach","Bangkok","Thailand",3),"beach","Beach","Bangkok, Thailand"),withPlaces(row("forest","Forest visit","Bangkok","Thailand",3),"national_park","Forest","Bangkok, Thailand"));
  assertThat(service.search("Thailand without \"beach\"","",null,0,12,"relevance").matching()).extracting(p->p.token()).containsExactly("forest");
  var event=service.search("Thailand no Christmas","",null,0,12,"relevance");assertThat(event.matching()).isEmpty();assertThat(event.searchAssist().blocked()).isTrue();
 }
 @Test void exactCalendarDayCannotMatchAnUnrelatedTitleNumber(){
  rows(dated(row("wrong","25 viewpoints","Bangkok","Thailand",3),1,true),dated(row("right","Quiet escape","Bangkok","Thailand",3),25,true));
  assertThat(service.search("Thailand December 25","",null,0,12,"relevance").matching()).extracting(p->p.token()).containsExactly("right");
  assertThat(service.search("Thailand December 32","",null,0,12,"relevance").searchAssist().blocked()).isTrue();
 }
 @Test void publicChristmasDatesRankNearerTheEventFirst(){
  rows(dated(row("early","Culture visit","Bangkok","Thailand",3),3,true),dated(row("near","Culture visit","Bangkok","Thailand",3),25,true));
  assertThat(service.search("Thailand Christmas","",null,0,12,"relevance").related()).extracting(p->p.token()).containsExactly("near","early");
 }
 @Test void natureLedTripsRankAheadOfIncidentalNatureStops(){
  var focused=withPlaces(row("focused","Quiet visit","Bangkok","Thailand",3),"national_park","Forest","Bangkok, Thailand");
  var incidental=withPlaces(row("incidental","Quiet visit","Bangkok","Thailand",3),"botanical_garden","Garden","Bangkok, Thailand");
  incidental.setSnapshot(incidental.getSnapshot().replace("\"items\":[{","\"items\":[{\"type\":\"place\",\"placeType\":\"shopping_mall\",\"name\":\"Mall\"},{"));
  rows(incidental,focused);
  assertThat(service.search("Thailand nature","",null,0,12,"relevance").matching()).extracting(p->p.token()).containsExactly("focused","incidental");
 }
}
