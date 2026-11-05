package com.navio.tripplanningservice.service.publication;

import java.util.*;
import java.util.regex.Pattern;

final class SearchIntentParser {
 private static final Set<String> FILLER=Set.of("a","an","the","and","or","in","of","for","to","with","on","my","our","your","trip","trips","plan","plans","itinerary","itineraries","travel","travelling","traveling","focus","focused","focusing","tour","tours","route","routes","holiday","holidays","vacation","vacations","break","breaks","i","want","would","love","please","some","include","including","visit","visiting","explore","exploring","enjoy","enjoying","that","is","are","like","such","as","e","g","prefer","preferred","preferably","no","not","without","avoid","avoiding","but","only","also","at","near","far","from","around","something");
 static SearchIntent parse(String raw,Collection<PlanSearchQuery.Location> locations,String selected,Integer selectedDays){
  String normalized=TravelConcepts.normal(raw);
  var quoted=new ArrayList<String>();var excludedQuoted=new ArrayList<String>();var quoteMatcher=Pattern.compile("\"([^\"]+)\"").matcher(raw);while(quoteMatcher.find()){
   String prefix=TravelConcepts.normal(raw.substring(0,quoteMatcher.start())).replaceAll(".*\\b(?:but|with)\\b","");
   (prefix.matches("(?s).*\\b(?:without|avoid|avoiding|no|not)\\b.*")?excludedQuoted:quoted).add(TravelConcepts.normal(quoteMatcher.group(1)));
  }
  String analysisRaw=quoteMatcher.replaceAll(" ");var legacy=PlanSearchQuery.parse(analysisRaw,locations,selected);
  String remaining=TravelConcepts.normal(analysisRaw);var notices=new ArrayList<String>();boolean blocked=false;
  var spans=new ArrayList<SearchIntent.Span>();
  var geo=new ArrayList<>(legacy.geography);
  for(var g:geo){String name=TravelConcepts.normal(g.name());var m=Pattern.compile("(?<![\\p{L}\\p{N}])"+String.join("\\s*",Arrays.stream(name.split(" ")).map(Pattern::quote).toList())+"(?![\\p{L}\\p{N}])").matcher(remaining);while(m.find())spans.add(new SearchIntent.Span(m.start(),m.end(),"geography",g.name()));remaining=m.replaceAll(match->" ".repeat(match.group().length()));}
  Set<String> countryCodes=new HashSet<>();for(var g:geo)if(!g.country().isBlank())countryCodes.add(g.country());
  var geographicSpans=spans.stream().filter(s->s.kind().equals("geography")).sorted(Comparator.comparingInt(SearchIntent.Span::start)).toList();
  boolean geographyAny=false;String analysisNormal=TravelConcepts.normal(analysisRaw);
  for(int i=1;i<geographicSpans.size();i++)if(analysisNormal.substring(geographicSpans.get(i-1).end(),geographicSpans.get(i).start()).contains(" or "))geographyAny=true;
  if(!selected.isBlank()&&!countryCodes.isEmpty()&&(!geographyAny&&!countryCodes.equals(Set.of(selected))||geographyAny&&!countryCodes.contains(selected))){notices.add("Your search destination conflicts with the selected country. Edit the search or clear the country filter.");blocked=true;}
  if(geo.stream().anyMatch(g->g.country().isBlank())){notices.add("This destination has more than one location. Select a country to clarify your search.");blocked=true;}
  if(geo.isEmpty()&&!selected.isBlank())geo.add(new PlanSearchQuery.Location(Locale.of("",selected).getDisplayCountry(Locale.ENGLISH),selected,true));
  var geographicGroups=new ArrayList<List<PlanSearchQuery.Location>>();SearchIntent.Span priorGeo=null;
  for(var span:geographicSpans){var location=geo.stream().filter(g->g.name().equals(span.value())).findFirst().orElseThrow();
   if(priorGeo!=null&&analysisNormal.substring(priorGeo.end(),span.start()).contains(" or ")){var group=new ArrayList<>(geographicGroups.removeLast());group.add(location);geographicGroups.add(List.copyOf(group));}
   else geographicGroups.add(List.of(location));priorGeo=span;
  }
  if(geographicGroups.isEmpty())geo.forEach(g->geographicGroups.add(List.of(g)));
  if(geographicSpans.stream().filter(s->geo.stream().anyMatch(g->g.name().equals(s.value())&&g.countryLevel())).count()>1&&analysisNormal.contains(" and ")&&analysisNormal.contains(" or ")){blocked=true;notices.add("The destination combination is ambiguous. Use either ‘and’ or ‘or’ between countries, or select a country.");}
  if(analysisNormal.matches("(?s).*\\b(?:no|not|without|avoid)\\s+(?:christmas|xmas|new year|january|february|march|april|may|june|july|august|september|october|november|december)\\b.*")){blocked=true;notices.add("Please clarify this event or month exclusion. It will not be treated as a positive event request.");}
  if(legacy.temporal.invalidDate()){blocked=true;notices.add("Enter a valid calendar month and day.");}
  var duration=Pattern.compile("\\b(\\d+)\\s*(days?|months?|years?)\\b").matcher(remaining);Integer days=selectedDays;
  if(duration.find()){try{int value=Math.multiplyExact(Integer.parseInt(duration.group(1)),duration.group(2).startsWith("month")?30:duration.group(2).startsWith("year")?365:1);if(value<1||value>36500)throw new ArithmeticException();if(days!=null&&days!=value){blocked=true;notices.add("The duration in your search conflicts with the selected trip duration.");}days=value;}catch(RuntimeException e){blocked=true;notices.add("Enter a valid trip duration between 1 and 36,500 days.");}remaining=duration.replaceAll(match->" ".repeat(match.group().length()));}
  if(Pattern.compile("\\b(?:at least|at most|under|over|between|less than|more than)\\b").matcher(normalized).find()||raw.matches(".*\\d\\s*[-–]\\s*\\d.*")){blocked=true;notices.add("Ranges and numeric limits cannot be verified by this search yet. Try an exact duration or a broader search.");}
  if(Pattern.compile("\\b(?:wheelchair|accessible|accessibility|free admission|public holiday|winter|summer|autumn|spring)\\b").matcher(normalized).find()){blocked=true;notices.add("This requirement needs more specific evidence. Try a month or a named activity; seasonal conditions and accessibility are not inferred.");}
  remaining=legacy.temporal.remove(remaining);
  if(legacy.temporal.yearRequested()){blocked=true;notices.add("Published trip dates show month and day only; a travel year cannot be verified.");}
  var required=new ArrayList<Set<String>>();var excluded=new LinkedHashSet<String>();var preferred=new LinkedHashSet<String>();var examples=new LinkedHashSet<String>();
  for(var quotedExclusion:excludedQuoted)TravelConcepts.ALIASES.stream().filter(alias->alias.getKey().equals(quotedExclusion)).forEach(alias->excluded.add(alias.getValue()));
  String conceptsText=remaining;var mentions=TravelConcepts.mentions(conceptsText);int priorEnd=0;String mode="required";
  for(var mention:mentions){
   String between=conceptsText.substring(priorEnd,mention.start());
   if(between.matches("(?s).*\\b(?:but|with|include|including)\\b.*"))mode="required";
   if(between.matches("(?s).*\\b(?:no|without|avoid|avoiding|not)\\b.*")&&!between.matches("(?s).*\\bnot only\\b.*"))mode="excluded";
   else if(between.matches("(?s).*\\b(?:prefer|preferred|preferably)\\b.*"))mode="preferred";
   else if(between.matches("(?s).*\\b(?:like|such as)\\b.*"))mode="examples";
   if(mode.equals("excluded"))excluded.add(mention.id());else if(mode.equals("preferred"))preferred.add(mention.id());else if(mode.equals("examples"))examples.add(mention.id());
   else if(!required.isEmpty()&&between.matches("(?s).*\\bor\\b.*")){var group=new LinkedHashSet<>(required.removeLast());group.add(mention.id());required.add(Set.copyOf(group));}
   else required.add(Set.of(mention.id()));
   spans.add(new SearchIntent.Span(mention.start(),mention.end(),mode,mention.id()));priorEnd=mention.end();
  }
  var clean=new StringBuilder(remaining);for(var m:mentions)clean.replace(m.start(),m.end()," ".repeat(m.end()-m.start()));remaining=clean.toString();
  // A comma-separated concept list is ambiguous; show the interpretation rather than hide an AND assumption.
  if(raw.contains(",")&&required.size()>1&&!normalized.contains(" and ")&&!normalized.contains(" or ")&&examples.isEmpty()){
   var any=new LinkedHashSet<String>();required.forEach(any::addAll);required.clear();required.add(Set.copyOf(any));notices.add("Interpreting the listed interests as alternatives: "+String.join(" or ",any.stream().map(TravelConcepts::label).toList())+".");
  }
  var terms=new ArrayList<>(Arrays.stream(remaining.split("\\s+")).filter(w->!w.isBlank()&&!FILLER.contains(w)).distinct().toList());terms.addAll(quoted);
  var unknownExclusion=Pattern.compile("\\b(?:without|avoid|avoiding|no|not)\\b(.*?)(?=\\b(?:but|with)\\b|$)").matcher(remaining);
  while(unknownExclusion.find())if(Arrays.stream(unknownExclusion.group(1).split("\\s+")).anyMatch(w->!w.isBlank()&&!FILLER.contains(w))){blocked=true;notices.add("This exclusion needs clarification. Try a specific place type or edit your search.");break;}
  if(!examples.isEmpty()&&required.isEmpty())required.add(Set.copyOf(examples));
  return new SearchIntent(raw,normalized,List.copyOf(geo),geographyAny,List.copyOf(geographicGroups),List.copyOf(excludedQuoted),List.copyOf(required),Set.copyOf(excluded),Set.copyOf(preferred),Set.copyOf(examples),List.copyOf(terms),legacy.temporal,days,List.copyOf(notices),blocked,List.copyOf(spans));
 }
}
