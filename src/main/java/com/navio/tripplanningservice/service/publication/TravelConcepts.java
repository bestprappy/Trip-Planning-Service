package com.navio.tripplanningservice.service.publication;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import java.util.regex.Pattern;

/** Versioned, directional travel vocabulary; no parent-to-child synonym expansion. */
final class TravelConcepts {
 record Concept(String id,String label,List<String> aliases,List<String> parents) {}
 record Dictionary(String version,List<Concept> concepts) {}
 record Mention(String id,int start,int end,String text) {}
 static final Dictionary DATA=load();
 static final Map<String,Concept> ALL;
 static final List<Map.Entry<String,String>> ALIASES;
 static {
  var concepts=new LinkedHashMap<String,Concept>();
  for(var c:DATA.concepts())if(concepts.put(c.id(),c)!=null)throw new IllegalStateException("Duplicate travel concept");
  ALL=Collections.unmodifiableMap(concepts);
  for(var id:ALL.keySet())validate(id,new HashSet<>());
  var aliases=new LinkedHashMap<String,String>();
  for(var c:ALL.values())for(var alias:c.aliases()){
   String prior=aliases.put(alias,c.id());if(prior!=null&&!prior.equals(c.id()))throw new IllegalStateException("Ambiguous travel alias");
  }
  ALIASES=aliases.entrySet().stream().sorted(Comparator.comparingInt((Map.Entry<String,String> a)->a.getKey().length()).reversed()).toList();
 }
 private static Dictionary load(){try(var input=TravelConcepts.class.getResourceAsStream("/search/travel-concepts.v1.json")){if(input==null)throw new IllegalStateException("Missing travel vocabulary");return new ObjectMapper().readValue(input,Dictionary.class);}catch(Exception e){throw new IllegalStateException("Invalid travel vocabulary",e);}}
 private static void validate(String id,Set<String> path){if(!ALL.containsKey(id)||!path.add(id))throw new IllegalStateException("Invalid travel concept graph");for(var parent:ALL.get(id).parents())validate(parent,new HashSet<>(path));}
 static String normal(String text){return SharedPlanSearchService.normal(text);}
 static boolean has(String text,String phrase){return (" "+text+" ").contains(" "+phrase+" ");}
 static List<Mention> mentions(String text){
  var found=new ArrayList<Mention>();var used=new BitSet(text.length());
  for(var alias:ALIASES){var matcher=Pattern.compile("(?<![\\p{L}\\p{N}])"+Pattern.quote(alias.getKey())+"(?![\\p{L}\\p{N}])").matcher(text);
   while(matcher.find()){if(used.get(matcher.start(),matcher.end()).isEmpty()){found.add(new Mention(alias.getValue(),matcher.start(),matcher.end(),matcher.group()));used.set(matcher.start(),matcher.end());}}}
  found.sort(Comparator.comparingInt(Mention::start));return found;
 }
 static Set<String> extract(String text){var result=new HashSet<String>();for(var m:mentions(normal(text)))addParents(m.id(),result);return Set.copyOf(result);}
 static void addParents(String id,Set<String> result){if(result.add(id))for(var parent:ALL.get(id).parents())addParents(parent,result);}
 static String label(String id){return ALL.get(id).label();}
}
