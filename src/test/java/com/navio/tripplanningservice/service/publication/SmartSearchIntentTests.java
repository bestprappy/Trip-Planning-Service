package com.navio.tripplanningservice.service.publication;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class SmartSearchIntentTests extends SearchTestFixtures {
 @Test void emptyCatalogueDoesNotInventHolidayIdeas(){
  rows();var result=service.search("Thailand Christmas Holiday","",null,0,12,"relevance");
  assertThat(result.querySuggestions()).isEmpty();
  assertThat(result.matching()).isEmpty();
 }
 @Test void examplesArePreferencesAndExplicitAndOrAreDifferent(){
  var mountain=withPlaces(row("mountain","Northern escape","Chiang Mai","Thailand",3),"national_park","Mountain viewpoint","Chiang Mai, Thailand");
  rows(mountain);
  assertThat(service.search("Scenic trip in Thailand focus nature like gardens forests mountains","",null,0,12,"relevance").matching()).hasSize(1);
  assertThat(service.search("Thailand mountains or gardens","",null,0,12,"relevance").matching()).hasSize(1);
  assertThat(service.search("Thailand mountains and gardens","",null,0,12,"relevance").matching()).isEmpty();
 }
 @Test void negativeQueryDoesNotRequireTheExcludedWord(){
  rows(withPlaces(row("mountain","Northern escape","Chiang Mai","Thailand",3),"national_park","Mountain viewpoint","Chiang Mai, Thailand"),withPlaces(row("coast","Coast escape","Hua Hin","Thailand",3),"beach","Beach","Hua Hin, Thailand"));
  assertThat(service.search("Thailand without beaches","",null,0,12,"relevance").matching()).extracting(p->p.token()).containsExactly("mountain");
 }
 @Test void temporalEvidenceCannotSkipAnotherRequiredFacet(){
  rows(dated(row("dec","December escape","Bangkok","Thailand",3),25,true));
  assertThat(service.search("Thailand Christmas Weekend","",null,0,12,"relevance").related()).isEmpty();
  assertThat(service.search("Thailand Christmas Festival","",null,0,12,"relevance").related()).isEmpty();
 }
 @Test void negativePlaceNamesDoNotBecomeScheduledActivities(){
  rows(withPlaces(row("cafe","Cafe visit","Bangkok","Thailand",3),"cafe","Christmas Cafe","Bangkok, Thailand"));
  assertThat(service.search("Thailand Christmas","",null,0,12,"relevance").matching()).isEmpty();
 }
}
