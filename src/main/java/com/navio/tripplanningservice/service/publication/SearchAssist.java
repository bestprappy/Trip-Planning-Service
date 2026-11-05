package com.navio.tripplanningservice.service.publication;

import java.util.*;

public record SearchAssist(int schemaVersion,String conceptVersion,String query,String country,Integer days,String sort,
 String fingerprint,String catalogueRevision,int maxQueryLength,String interpretation,List<String> notices,
 List<Suggestion> suggestions,boolean blocked) {
 public record Suggestion(String text,String kind,List<String> changedFacets,String availability,String label,Action action) {}
 public record Action(String query) {}
}
