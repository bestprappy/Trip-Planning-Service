package com.navio.tripplanningservice.service.publication;

import java.util.Set;
import java.util.stream.Collectors;

/** Seasons are publication keywords, not claims inferred from dates or climate. */
final class CatalogueSeasonIntent {
 private static final Set<String> SEASONS=Set.of("spring","summer","autumn","winter");
 private static Set<String> requested(SearchIntent intent){
  return intent.terms().stream().filter(SEASONS::contains).collect(Collectors.toSet());
 }
 static boolean constrained(SearchIntent intent){return !requested(intent).isEmpty();}
 static boolean supported(SearchIntent intent,PublicSearchEvidence item){
  return requested(intent).stream().allMatch(season->TravelConcepts.has(item.seasonText(),season));
 }
 static boolean preserved(SearchIntent intent,SearchIntent candidate){
  return requested(intent).stream().allMatch(season->TravelConcepts.has(candidate.normalized(),season));
 }
}
