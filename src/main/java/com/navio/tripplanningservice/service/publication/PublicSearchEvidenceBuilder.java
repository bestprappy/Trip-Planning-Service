package com.navio.tripplanningservice.service.publication;

import com.navio.tripplanningservice.dto.publication.*;
import java.time.MonthDay;
import java.util.*;
import java.util.function.Function;

final class PublicSearchEvidenceBuilder {
 private static <T> List<T> safe(List<T> values){return values==null?List.of():values;}
 static String affirmative(String text){
  if(text==null)return "";
  // Break contrast clauses before checking uncertainty; do not throw away a positive clause after “but”.
  return Arrays.stream(text.replaceAll("(?i)not only"," ").replaceAll("(?i)but also"," and ").split("(?i)(?<=[.!?;])|\\n|\\bbut\\b"))
   .filter(clause->!clause.contains("?")&&!TravelConcepts.normal(clause).matches(".*\\b(?:no|not|without|avoid|avoiding|maybe|might|perhaps|optional|consider|could|should)\\b.*"))
   .collect(java.util.stream.Collectors.joining(" "));
 }
 static PublicSearchEvidence build(PublicPlanSnapshot plan,Function<String,String> countryCode){
  var countries=new HashSet<String>();String primary=countryCode.apply(plan.destinationCountry());if(!primary.isEmpty())countries.add(primary);
  var text=new StringBuilder(affirmative(plan.title())).append(' ').append(Objects.toString(plan.destinationCity(),"")).append(' ').append(Objects.toString(plan.destinationCountry(),""));
  var events=new StringBuilder(affirmative(plan.title()));var strongText=new StringBuilder(text);var strong=new HashSet<>(TravelConcepts.extract(affirmative(plan.title())));var weak=new HashSet<String>();var scheduled=new HashSet<String>();
  if(plan.startingPart()!=null&&(plan.included()==null||plan.included().includeStartingPart())){String intro=affirmative(plan.startingPart().content());text.append(' ').append(intro);strongText.append(' ').append(intro);events.append(' ').append(intro);strong.addAll(TravelConcepts.extract(intro));}
  int count=0,nature=0;
  for(boolean itinerary:List.of(true,false))for(var day:itinerary?safe(plan.days()):safe(plan.lists()))if(day!=null){
   String heading=affirmative(day.title());text.append(' ').append(heading);if(itinerary)events.append(' ').append(heading);
   for(var item:safe(day.items()))if(item!=null){
    boolean place=Set.of("place","charger").contains(Objects.toString(item.type(),""));
    boolean notesVisible=plan.included()==null||plan.included().includeNotes();
    String owner=notesVisible?affirmative(String.join(" ",Objects.toString(item.notes(),""),Objects.toString(item.noteContent(),""))):"";
    String checklist=notesVisible?affirmative(String.join(" ",safe(item.checklistLabels()))):"";
    String placeText=place?String.join(" ",Objects.toString(item.name(),""),Objects.toString(item.placeType(),"").replace('_',' '),Objects.toString(item.description(),"")):"";
    String detail=String.join(" ",placeText,owner,checklist,Objects.toString(item.city(),""),Objects.toString(item.address(),""));text.append(' ').append(detail);
    Set<String> concepts=TravelConcepts.extract(placeText+" "+owner);
    if(itinerary){strong.addAll(concepts);strongText.append(' ').append(placeText).append(' ').append(owner);if(place){scheduled.addAll(TravelConcepts.extract(placeText));count++;if(concepts.contains("nature"))nature++;}events.append(' ').append(owner);}
    else weak.addAll(concepts);
    weak.addAll(TravelConcepts.extract(checklist));
    // Provider names are NOT event evidence (for example Christmas Cafe).
    if(item.charger()!=null){(itinerary?strong:weak).add("ev");}
    if(item.arrivalMode()!=null){(itinerary?strong:weak).addAll(TravelConcepts.extract(item.arrivalMode()));}
    if(place&&item.address()!=null){String[] parts=item.address().split(",");String code=countryCode.apply(parts[parts.length-1]);if(!code.isBlank())countries.add(code);}
   }
  }
  if(countries.contains("US")&&TravelConcepts.has(TravelConcepts.normal(text.toString()),"ca"))text.append(" California");
  var dates=new ArrayList<MonthDay>();if(plan.included()!=null&&plan.included().includeDates()){if(plan.startDate()!=null)dates.add(plan.startDate());safe(plan.days()).stream().filter(Objects::nonNull).map(PublicDayDto::date).filter(Objects::nonNull).forEach(dates::add);}
  return new PublicSearchEvidence(plan.title(),TravelConcepts.normal(text.toString()),TravelConcepts.normal(strongText.toString()),TravelConcepts.normal(events.toString()),Set.copyOf(countries),Set.copyOf(strong),Set.copyOf(weak),Set.copyOf(scheduled),List.copyOf(dates),count,nature);
 }
}
