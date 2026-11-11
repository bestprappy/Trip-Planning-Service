package com.navio.tripplanningservice.service.publication;

import java.util.*;

final class PlanSearchEligibility {
 /** Catalogue retrieval keeps hard constraints, then admits partial topical evidence as Relevant. */
 static PlanSearchQuery.Match catalogue(SearchIntent intent,PublicSearchEvidence item,int dayCount){
  var full=evaluate(intent,item,dayCount);
  if(intent.raw().isBlank())return full;
  boolean meaningful=intent.spans().stream().anyMatch(s->s.kind().equals("geography"))
      ||intent.days()!=null&&intent.raw().matches(".*\\d.*")||!intent.required().isEmpty()||!intent.terms().isEmpty()
      ||!intent.excluded().isEmpty()||intent.temporal().christmas()||intent.temporal().newYear()||!intent.temporal().months().isEmpty();
  if(!meaningful)return null;
  if(full!=null)return full;
  // Events, quoted names, exclusions, geography and durations cannot be relaxed by resemblance.
  if(intent.blocked()||intent.temporal().christmas()||intent.temporal().newYear()||intent.temporal().yearRequested()
      ||!intent.temporal().months().isEmpty()||!intent.temporal().calendarDays().isEmpty()
      ||intent.required().stream().anyMatch(g->g.contains("festival"))
      ||intent.terms().stream().anyMatch(t->t.contains(" ")||Set.of("lunar","chinese","songkran","christmas","easter","halloween","diwali","holi","ramadan","hanukkah","loy","krathong").contains(t)))return null;
  var constraints=new SearchIntent(intent.raw(),intent.normalized(),intent.geography(),intent.geographyAny(),intent.geographicGroups(),intent.excludedTerms(),
      List.of(),intent.excluded(),intent.preferred(),intent.examples(),List.of(),intent.temporal(),intent.days(),intent.notices(),intent.blocked(),intent.spans());
  if(evaluate(constraints,item,dayCount)==null)return null;
  var concepts=new HashSet<>(item.strong());concepts.addAll(item.weak());
  int score=0;
  for(var group:intent.required()){
   if(!Collections.disjoint(group,concepts))score+=100;
   else if(group.stream().anyMatch(c->family(c,concepts)))score+=30;
  }
  var words=Arrays.asList(item.text().split(" "));
  for(var term:intent.terms())if(words.stream().anyMatch(w->w.equals(term)||term.length()>=2&&w.startsWith(term)||term.length()>=5&&oneEdit(term,w)))score+=40;
  if(score==0)return null;
  String title=TravelConcepts.normal(item.title());
  if(title.contains(intent.normalized()))score+=80;
  return new PlanSearchQuery.Match(false,score,"Related public title or travel interest");
 }
 private static boolean family(String concept,Set<String> evidence){
  for(var family:List.of(Set.of("food","restaurant","cafe","street-food","dessert","seafood","sushi"),Set.of("nature","scenic","hike","garden","forest","mountain","waterfall","beach","national-park")))
   if((Set.of("food","restaurant","cafe","street-food","dessert").contains(concept)&&family.contains("food")
       ||Set.of("nature","scenic","hike").contains(concept)&&family.contains("nature"))&&!Collections.disjoint(family,evidence))return true;
  return false;
 }
 static PlanSearchQuery.Match evaluate(SearchIntent intent,PublicSearchEvidence item,int dayCount){
  if(intent.blocked()||intent.days()!=null&&intent.days()!=dayCount)return null;
  var geo=intent.geography();
  java.util.function.Predicate<PlanSearchQuery.Location> geoMatch=g->(g.country().isBlank()||item.countries().contains(g.country()))&&(g.countryLevel()||java.util.regex.Pattern.compile("(?<![\\p{L}\\p{N}])"+String.join("\\s*",Arrays.stream(TravelConcepts.normal(g.name()).split(" ")).map(java.util.regex.Pattern::quote).toList())+"(?![\\p{L}\\p{N}])").matcher(item.text()).find());
  if(intent.geographicGroups().stream().anyMatch(group->group.stream().noneMatch(geoMatch)))return null;
  if(intent.excludedTerms().stream().anyMatch(term->TravelConcepts.has(item.strongText(),term)))return null;
  if(!Collections.disjoint(intent.excluded(),item.scheduled())||!Collections.disjoint(intent.excluded(),item.strong()))return null;
  var all=new HashSet<>(item.strong());all.addAll(item.weak());boolean direct=true;
  for(var group:intent.required()){
   if(Collections.disjoint(group,all))return null;
   if(Collections.disjoint(group,item.strong()))direct=false;
  }
  // Unknown specific terms are never dropped for a broad category match.
  var words=Arrays.asList(item.text().split(" "));
  for(String term:intent.terms()){
   if(term.contains(" ")){if(!TravelConcepts.has(item.text(),term))return null;if(!TravelConcepts.has(item.strongText(),term))direct=false;continue;}
   boolean exact=words.stream().anyMatch(w->w.equals(term)||term.length()>=2&&w.startsWith(term));
   if(!exact){if(term.length()<5||words.stream().noneMatch(w->oneEdit(term,w)))return null;direct=false;}else if(Arrays.stream(item.strongText().split(" ")).noneMatch(w->w.equals(term)||term.length()>=2&&w.startsWith(term)))direct=false;
  }
  var time=intent.temporal().match(item.eventText(),item.dates());if(!time.eligible())return null;
  direct&=time.direct();
  int score=intent.required().size()*100+intent.terms().size()*40+time.score();
  score+=intent.preferred().stream().filter(item.strong()::contains).count()*15;
  score+=intent.examples().stream().filter(item.strong()::contains).count()*10;
  if(intent.requires("nature")||intent.requires("scenic"))score+=item.stopCount()>0?Math.round(50f*item.natureStops()/item.stopCount()):0;
  String title=TravelConcepts.normal(item.title());if(!intent.normalized().isBlank())score+=title.equals(intent.normalized())?140:title.startsWith(intent.normalized())?80:title.contains(intent.normalized())?40:0;
  String reason=!time.reason().isEmpty()?time.reason():!direct?"Supported by optional public content or a close spelling match":"Matches your destination and travel interests";
  return new PlanSearchQuery.Match(direct,score,reason);
 }
 private static boolean oneEdit(String a,String b){if(Math.abs(a.length()-b.length())>1)return false;int i=0,j=0,edits=0;while(i<a.length()&&j<b.length()){if(a.charAt(i)==b.charAt(j)){i++;j++;}else{if(++edits>1)return false;if(a.length()>=b.length())i++;if(b.length()>=a.length())j++;}}return edits+a.length()-i+b.length()-j<=1;}
}
