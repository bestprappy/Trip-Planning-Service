package com.navio.tripplanningservice.service.publication;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.DynamicTest;
import java.util.List;
import java.util.stream.Stream;
import static org.assertj.core.api.Assertions.*;

class CatalogueSeasonSearchTests extends SearchTestFixtures {
 @Test void completionNeverOffersAQueryThatItsOwnParserRejects(){
  var value=row("mixed","Kyoto food. Not Spring.","Kyoto","Japan",3);
  value.setSnapshot(value.getSnapshot().replace("\"days\":[]","\"days\":[],\"startingPart\":{\"title\":\"Notes\",\"content\":\"Spring food walk\"}"));
  rows(value);
  var result=service.search("Kyoto Spring food","",null,0,12,"relevance");
  assertThat(result.matching()).hasSize(1);
  assertThat(result.querySuggestions()).noneMatch(s->s.text().contains("Not Spring"));
 }
 @TestFactory Stream<DynamicTest> seasonalCountryDurationAndSortingMatrix(){
  return Stream.of("Spring","Summer","Autumn","Winter").flatMap(season->Stream.of("","JP","TH")
   .flatMap(country->Stream.of(0,2,3).flatMap(days->Stream.of("relevance","recent","views","rating")
    .map(sort->DynamicTest.dynamicTest(season+"/"+country+"/"+days+"/"+sort,()->{
     rows(row("wanted","Kyoto "+season+" food walk","Kyoto","Japan",2),
       row("other","Kyoto "+(season.equals("Spring")?"Autumn":"Spring")+" food walk","Kyoto","Japan",2));
     var result=service.search("Kyoto "+season+" food",""+country,days==0?null:days,0,12,sort);
     var all=Stream.concat(result.matching().stream(),result.related().stream()).map(p->p.token()).toList();
     if(!country.equals("TH")&&days!=3)assertThat(all).containsExactly("wanted");else assertThat(all).isEmpty();
     assertThat(result.searchAssist().blocked()).isEqualTo(country.equals("TH"));
     assertThat(result.querySuggestions()).allMatch(s->s.text().contains(season));
    })))));
 }
 @Test void optionalSeasonEvidenceIsRelevantAndNegatedSeasonIsNotEvidence(){
  var optional=row("optional","Kyoto food walk","Kyoto","Japan",3);
  optional.setSnapshot(optional.getSnapshot().replace("\"days\":[]","\"days\":[],\"lists\":[{\"items\":[{\"type\":\"note\",\"noteContent\":\"Spring food ideas\"}]}]"));
  rows(optional,row("negative","Kyoto food walk. Not a Spring trip.","Kyoto","Japan",3));
  var result=service.search("Kyoto Spring food","",null,0,12,"relevance");
  assertThat(result.matching()).isEmpty();
  assertThat(result.related()).extracting(p->p.token()).containsExactly("optional");
 }
 @Test void uncertainOrNegatedPlaceDescriptionsAndGeographicNamesDoNotSupplySeasons(){
  for(String container:List.of("days","lists"))for(String description:List.of("Not a Spring trip.","Maybe Spring is suitable.","Avoid Spring visits.")){
   var value=row("negative","Kyoto food walk","Kyoto","Japan",3);
   value.setSnapshot(value.getSnapshot().replace("\"days\":[]","\""+container+"\":[{\"items\":[{\"type\":\"place\",\"name\":\"Restaurant\",\"description\":\""+description+"\"}]}]"));
   rows(value);
   var result=service.search("Kyoto Spring food","",null,0,12,"relevance");
   assertThat(result.matching()).as(container+description).isEmpty();assertThat(result.related()).as(container+description).isEmpty();
  }
  rows(withPlaces(row("venue","Kyoto food walk","Kyoto","Japan",3),"restaurant","Spring Garden","Kyoto, Japan"));
  assertThat(service.search("Kyoto Spring food","",null,0,12,"relevance").matching()).isEmpty();
  assertThat(service.search("Kyoto \"Spring Garden\"","",null,0,12,"relevance").matching()).hasSize(1);
 }
 @Test void allFourSeasonsRetrieveTheirPublishedTitlesWithoutCrossSeasonMatches(){
  rows(row("spring","Kyoto Spring gardens","Kyoto","Japan",3),row("summer","Kyoto Summer gardens","Kyoto","Japan",3),
      row("autumn","Kyoto Autumn gardens","Kyoto","Japan",3),row("winter","Kyoto Winter gardens","Kyoto","Japan",3));
  for(String season:List.of("Spring","Summer","Autumn","Winter")){
   var result=service.search("Kyoto "+season,"",null,0,12,"relevance");
   assertThat(result.matching()).as(season).extracting(p->p.token()).containsExactly(season.toLowerCase());
   assertThat(result.related()).as(season).isEmpty();
  }
 }
 @Test void seasonalResemblanceNeverDropsSeasonFromResultsOrSearchCompletions(){
  rows(row("spring","Kyoto Spring dessert walk","Kyoto","Japan",3),row("autumn","Kyoto Autumn food walk","Kyoto","Japan",3));
  var result=service.search("Kyoto Spring restaurants","",null,0,12,"relevance");
  assertThat(result.related()).extracting(p->p.token()).containsExactly("spring");
  assertThat(result.querySuggestions()).isNotEmpty().allMatch(s->s.text().contains("Spring"));
 }
 @Test void countryAndDurationStillRestrictSeasonalSearch(){
  rows(row("short","Kyoto Spring gardens","Kyoto","Japan",2),row("long","Kyoto Spring gardens","Kyoto","Japan",3),
      row("thai","Bangkok Spring gardens","Bangkok","Thailand",2));
  assertThat(service.search("Spring","JP",2,0,12,"relevance").matching()).extracting(p->p.token()).containsExactly("short");
 }
 @Test void caseAndQuotedSeasonTitlesRemainSearchable(){
  rows(row("spring","Kyoto Spring gardens","Kyoto","Japan",3));
  for(String query:List.of("KYOTO SPRING","\"Kyoto Spring\""))
   assertThat(service.search(query,"",null,0,12,"relevance").matching()).as(query).extracting(p->p.token()).containsExactly("spring");
 }
 @Test void seasonIsNotInferredFromDatesOrLongerPlaceNames(){
  rows(dated(row("date","Kyoto food walk","Kyoto","Japan",3),25,true),
      row("name","Springfield food walk","Springfield","United States",3));
  var result=service.search("Spring food","",null,0,12,"relevance");
  assertThat(result.matching()).isEmpty();assertThat(result.related()).isEmpty();
 }
 @Test void unsupportedClaimsAndYearsStillCannotBeRelaxed(){
  rows(row("spring","Kyoto Spring accessible gardens","Kyoto","Japan",3));
  for(String query:List.of("Kyoto Spring wheelchair","Kyoto Spring free admission","Kyoto Spring 2027")){
   var result=service.search(query,"",null,0,12,"relevance");
   assertThat(result.matching()).as(query).isEmpty();assertThat(result.related()).as(query).isEmpty();
  }
 }
}
