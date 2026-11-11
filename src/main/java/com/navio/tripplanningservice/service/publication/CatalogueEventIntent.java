package com.navio.tripplanningservice.service.publication;

import java.util.*;

/** Shared named-occasion vocabulary. Aliases identify intent; public event evidence supplies support. */
final class CatalogueEventIntent {
 private static final List<String> OCCASIONS=List.of(
     "lunar new year|chinese new year|tet", "songkran", "easter|orthodox easter", "halloween",
     "diwali|deepavali", "holi", "ramadan|ramazan", "eid al fitr|eid ul fitr", "eid al adha", "eid",
     "hanukkah|chanukah", "loy krathong|loi krathong", "yi peng|yee peng", "thanksgiving",
     "carnival|carnaval|mardi gras", "oktoberfest", "vesak|visakha bucha|buddha day",
     "chuseok", "obon", "tanabata", "golden week", "mid autumn festival|moon festival",
     "dragon boat festival|duanwu", "qingming", "nauryz|nowruz|newroz", "rosh hashanah",
     "yom kippur", "passover|pesach", "purim", "navratri", "dussehra|dasara",
     "ganesh chaturthi", "onam", "pongal", "thaipusam", "thaipongal",
     "st patricks day|saint patricks day", "valentines day|valentine day", "carnaval de barranquilla");
 private static Set<String> named(String text){
  var found=new HashSet<String>();
  for(String occasion:OCCASIONS){var aliases=occasion.split("\\|");if(Arrays.stream(aliases).anyMatch(a->TravelConcepts.has(text,a)))found.add(aliases[0]);}
  // Productive event-name endings cover catalogue-specific names such as Wonderfest.
  var festival=java.util.regex.Pattern.compile("\\b[\\p{L}]+fest(?:ival)?\\b").matcher(text);
  while(festival.find())found.add(festival.group());
  return found;
 }
 private static Set<String> named(SearchIntent intent){
  String text=" "+intent.normalized()+" ";
  for(String phrase:intent.excludedTerms())text=text.replace(" "+phrase+" "," ");
  // A quoted venue/name is an exact phrase, not an affirmative event request.
  for(String phrase:intent.terms())if(phrase.contains(" "))text=text.replace(" "+phrase+" "," ");
  return named(text.strip());
 }
 static boolean constrained(SearchIntent intent){
  var time=intent.temporal();
  return !named(intent).isEmpty()||time.christmas()||time.newYear()||time.yearRequested()
      ||!time.months().isEmpty()||!time.calendarDays().isEmpty()||intent.required().stream().anyMatch(g->g.contains("festival"));
 }
 static boolean supported(SearchIntent intent,PublicSearchEvidence evidence){var required=named(intent);return required.isEmpty()||named(evidence.eventText()).containsAll(required);}
 static boolean preserved(SearchIntent intent,SearchIntent candidate){
  return candidate.temporal().equals(intent.temporal())&&Objects.equals(candidate.days(),intent.days())
      &&named(candidate).containsAll(named(intent))
      &&intent.terms().stream().filter(t->t.contains(" ")).allMatch(t->TravelConcepts.has(candidate.normalized(),t));
 }
 private CatalogueEventIntent() {}
}
