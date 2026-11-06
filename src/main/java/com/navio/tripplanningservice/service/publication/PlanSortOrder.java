package com.navio.tripplanningservice.service.publication;
import java.util.*;
/** Ordered priorities; preserves the single-sort wire contract and bounds public input. */
final class PlanSortOrder {
 private PlanSortOrder() {}
 static List<String> parse(String value){
  if(value==null||value.isEmpty()||value.length()>100)throw new IllegalArgumentException("Invalid sorting priorities");
  var priorities=Arrays.asList(value.split(",",-1));
  if(priorities.size()>6||new HashSet<>(priorities).size()!=priorities.size()||!PlanDiscovery.SORTS.containsAll(priorities)||priorities.size()>1&&priorities.contains("relevance"))throw new IllegalArgumentException("Invalid sorting priorities");
  return List.copyOf(priorities);
 }
}
