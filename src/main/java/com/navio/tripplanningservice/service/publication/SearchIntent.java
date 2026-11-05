package com.navio.tripplanningservice.service.publication;

import java.util.*;

record SearchIntent(String raw,String normalized,List<PlanSearchQuery.Location> geography,boolean geographyAny,List<List<PlanSearchQuery.Location>> geographicGroups,List<String> excludedTerms,
 List<Set<String>> required,Set<String> excluded,Set<String> preferred,Set<String> examples,
 List<String> terms,PlanSearchTemporalIntent temporal,Integer days,List<String> notices,boolean blocked,
 List<Span> spans) {
 record Span(int start,int end,String kind,String value) {}
 String geographySignature(){return geographicGroups.stream().map(group->group.stream().map(g->TravelConcepts.normal(g.name())+":"+g.country()).sorted().toList().toString()).sorted().toList().toString();}
 String signature(){return geographySignature()+"|"+excludedTerms.stream().sorted().toList()+"|"+required.stream().map(g->g.stream().sorted().toList().toString()).sorted().toList()+"|"+excluded.stream().sorted().toList()+"|"+preferred.stream().sorted().toList()+"|"+examples.stream().sorted().toList()+"|"+terms.stream().sorted().toList()+"|"+temporal+"|"+days+"|"+blocked;}
 boolean requires(String concept){return required.stream().anyMatch(g->g.contains(concept));}
}
