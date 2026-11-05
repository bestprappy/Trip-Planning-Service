package com.navio.tripplanningservice.service.publication;

import java.util.*;
import java.util.function.ToLongFunction;

final class QuerySuggestionComposer {
 static List<SearchAssist.Suggestion> compose(SearchIntent intent,Collection<PlanSearchQuery.Location> locations,String selected,Integer days,ToLongFunction<SearchIntent> support){
  if(intent.normalized().length()<2)return List.of();
  var result=new ArrayList<SearchAssist.Suggestion>();var seen=new HashSet<String>();
  String place=intent.geographicGroups().stream().map(group->group.stream().map(PlanSearchQuery.Location::name).distinct().collect(java.util.stream.Collectors.joining(" or "))).collect(java.util.stream.Collectors.joining(" and "));
  if(place.isBlank()&&!selected.isBlank())place=Locale.of("",selected).getDisplayCountry(Locale.ENGLISH);
  if(place.isBlank())return List.of();
  String location=" in "+place;
  String canonical=render(intent,location);
  add(result,seen,intent,canonical,"rewrite",List.of(),"Same search, clearer wording",locations,selected,days,support);
  if(!intent.blocked()&&intent.temporal().christmas()&&intent.required().isEmpty()&&intent.terms().isEmpty()&&intent.excluded().isEmpty()&&intent.preferred().isEmpty()&&intent.examples().isEmpty()){
   add(result,seen,intent,"Christmas festival trips"+location,"refine",List.of("interests"),"Add festivals",locations,selected,days,support);
   add(result,seen,intent,"Christmas weekend trips"+location,"refine",List.of("interests"),"Add weekend trips",locations,selected,days,support);
   add(result,seen,intent,"December holiday trips"+location,"broaden",List.of("dates"),"Broader dates: December",locations,selected,days,support);
  }
  if(intent.temporal().yearRequested())add(result,seen,intent,intent.raw().replaceAll("\\b(?:19|20|21)\\d{2}\\b","").strip(),"broaden",List.of("dates"),"Search without a travel year",locations,selected,days,support);
  if(!intent.blocked())for(int i=0;i<intent.required().size()&&i<6;i++){
   var relaxed=new ArrayList<>(intent.required());var removed=relaxed.remove(i);
   var alternative=new SearchIntent(intent.raw(),intent.normalized(),intent.geography(),intent.geographyAny(),intent.geographicGroups(),intent.excludedTerms(),List.copyOf(relaxed),intent.excluded(),intent.preferred(),intent.examples(),intent.terms(),intent.temporal(),intent.days(),List.of(),false,intent.spans());
   String phrase=render(alternative,location);
   var parsed=SearchIntentParser.parse(phrase,locations,selected,days);
   if(support.applyAsLong(parsed)>0)add(result,seen,intent,phrase,"broaden",List.of("interests"),"Without requiring "+String.join(" or ",removed.stream().map(TravelConcepts::label).toList()),locations,selected,days,support);
  }
  // Narrow categories only when actual catalogue evidence supports them.
  if(!intent.blocked()&&(intent.requires("nature")||intent.requires("scenic"))&&intent.required().size()<=2&&intent.excluded().isEmpty())
   for(String facet:List.of("garden","forest","mountain","waterfall")){
    String phrase=canonical+" with "+TravelConcepts.label(facet);
    var parsed=SearchIntentParser.parse(phrase,locations,selected,days);if(support.applyAsLong(parsed)>0)add(result,seen,intent,phrase,"refine",List.of("interests"),"Focus on "+TravelConcepts.label(facet),locations,selected,days,support);
   }
  return result.stream().sorted(Comparator.comparingInt((SearchAssist.Suggestion s)->s.kind().equals("rewrite")?0:s.availability().equals("available")?1:2)).limit(5).toList();
 }
 private static String render(SearchIntent intent,String location){
  var bits=new ArrayList<String>();
  if(intent.temporal().christmas())bits.add("Christmas");else if(intent.temporal().newYear())bits.add("New Year");
  else if(!intent.temporal().calendarDays().isEmpty())bits.add(String.join(" and ",intent.temporal().calendarDays().stream().sorted().map(d->d.getMonth().getDisplayName(java.time.format.TextStyle.FULL,Locale.ENGLISH)+" "+d.getDayOfMonth()).toList()));
  else if(!intent.temporal().months().isEmpty())bits.add(String.join(" and ",intent.temporal().months().stream().sorted().map(m->java.time.Month.of(m).getDisplayName(java.time.format.TextStyle.FULL,Locale.ENGLISH)).toList()));
  for(var group:intent.required())bits.add(String.join(" or ",group.stream().sorted().map(TravelConcepts::label).toList()));
  bits.addAll(intent.terms().stream().map(term->term.contains(" ")?'"'+term+'"':term).toList());if(bits.isEmpty())bits.add("holiday");else if(intent.temporal().christmas()&&intent.required().isEmpty()&&intent.terms().isEmpty())bits.add("holiday");
  String canonical=String.join(" and ",bits)+" trips"+location;
  canonical=canonical.replace("Christmas and ","Christmas ").replace("New Year and ","New Year ");
  if(intent.days()!=null)canonical=intent.days()+" day "+canonical;
  if(!intent.excluded().isEmpty())canonical+=" without "+String.join(" or ",intent.excluded().stream().sorted().map(TravelConcepts::label).toList());
  if(!intent.excludedTerms().isEmpty())canonical+=" without "+String.join(" and ",intent.excludedTerms().stream().map(t->'"'+t+'"').toList());
  if(!intent.preferred().isEmpty())canonical+=", prefer "+String.join(" and ",intent.preferred().stream().sorted().map(TravelConcepts::label).toList());
  if(!intent.examples().isEmpty())canonical+=" like "+String.join(" or ",intent.examples().stream().sorted().map(TravelConcepts::label).toList());
  return canonical;
 }
 private static void add(List<SearchAssist.Suggestion> result,Set<String> seen,SearchIntent original,String text,String kind,List<String> changed,String label,Collection<PlanSearchQuery.Location> locations,String country,Integer days,ToLongFunction<SearchIntent> support){
  if(text.codePointCount(0,text.length())>256||!seen.add(TravelConcepts.normal(text))||TravelConcepts.normal(text).equals(original.normalized()))return;
  var parsed=SearchIntentParser.parse(text,locations,country,days);
  if(original.days()!=null&&parsed.days()==null){text=original.days()+" day "+text;parsed=SearchIntentParser.parse(text,locations,country,days);}
  if(kind.equals("rewrite")&&!parsed.signature().equals(original.signature()))return;
  if(!kind.equals("rewrite")&&(!Objects.equals(original.days(),parsed.days())||!original.excluded().equals(parsed.excluded())||!original.excludedTerms().equals(parsed.excludedTerms())||!original.terms().equals(parsed.terms())||!original.geographySignature().equals(parsed.geographySignature())))return;
  if(!changed.contains("dates")&&!original.temporal().equals(parsed.temporal()))return;
  if(!original.preferred().equals(parsed.preferred())||!original.examples().equals(parsed.examples()))return;
  if(parsed.blocked())return;
  long count=support.applyAsLong(parsed);
  // Broader/refined ideas remain labelled as ideas, never a promise of inventory.
  result.add(new SearchAssist.Suggestion(text,kind,changed,count>0?"available":"none",label,new SearchAssist.Action(text)));
 }
}
