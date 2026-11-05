package com.navio.tripplanningservice.service.publication;

import java.util.*;

final class PlanSearchEligibility {
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
