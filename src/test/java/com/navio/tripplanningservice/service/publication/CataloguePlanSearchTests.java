package com.navio.tripplanningservice.service.publication;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class CataloguePlanSearchTests extends SearchTestFixtures {
 @Test void partialMeaningfulTitleEvidenceIsRelevantButCompleteTitleRanksFirst(){
  rows(row("partial","Bangkok food markets","Bangkok","Thailand",3),row("exact","Bangkok food markets sunset","Bangkok","Thailand",3),row("other","Bangkok temples","Bangkok","Thailand",3));
  var result=service.search("Bangkok food markets sunset","",null,0,12,"relevance");
  assertThat(result.matching()).extracting(p->p.token()).containsExactly("exact");
  assertThat(result.related()).extracting(p->p.token()).containsExactly("partial");
 }
 @Test void broadFoodFamilyFindsDessertAndStreetFoodWithoutDroppingDestination(){
  rows(row("sweet","Bangkok dessert crawl","Bangkok","Thailand",3),row("street","Bangkok street food","Bangkok","Thailand",3),row("wrong","Tokyo dessert","Tokyo","Japan",3));
  var result=service.search("Bangkok restaurants","",null,0,12,"relevance");
  assertThat(result.related()).extracting(p->p.token()).containsExactlyInAnyOrder("sweet","street");
 }
 @Test void suggestionsAreGroundedAndNoAlternativesAreFabricated(){
  rows(row("food","Bangkok street food","Bangkok","Thailand",3),row("private","Tokyo temples","Tokyo","Japan",3));
  var result=service.search("Bangkok food","",null,0,12,"relevance");
  assertThat(result.querySuggestions()).isNotEmpty().allMatch(s->s.source().equals("catalogue"));
  assertThat(result.querySuggestions()).extracting(SharedPlanSearchService.QuerySuggestion::text).allMatch(s->s.contains("Bangkok")&&!s.contains("Tokyo"));
  assertThat(result.searchAssist().suggestions()).isEmpty();
  rows();assertThat(service.search("Thailand Christmas Holiday","",null,0,12,"relevance").querySuggestions()).isEmpty();
 }
 @Test void fillerAndUnrelatedUnknownWordsNeverRetrieveCountryOnlyPlans(){
  rows(row("food","Bangkok food","Bangkok","Thailand",3));
  assertThat(service.search("the itinerary please","",null,0,12,"relevance").matching()).isEmpty();
  assertThat(service.search("the itinerary please","TH",null,0,12,"relevance").matching()).isEmpty();
  assertThat(service.search("Bangkok astrophysics","",null,0,12,"relevance").related()).isEmpty();
 }
 @Test void namedEventIntentCannotDisappearIntoFoodResemblance(){
  rows(row("food","Bangkok food markets","Bangkok","Thailand",3));
  assertThat(service.search("Bangkok Easter food","",null,0,12,"relevance").related()).isEmpty();
 }
 @Test void suggestionsKeepDestinationAndSpecialEventIntent(){
  rows(row("christmas","Christmas markets","Bangkok","Thailand",3));
  var result=service.search("Thailand Christmas festival","",null,0,12,"relevance");
  assertThat(result.querySuggestions()).isNotEmpty().allMatch(s->s.text().contains("Christmas")&&s.text().contains("Thailand"));
 }
}
