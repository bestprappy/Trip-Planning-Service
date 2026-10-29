package com.navio.tripplanningservice.dto.publication;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/** Explicit selections. Missing fields are closed; catalogue inclusion requires includeGarage. */
public record PublicationOptions(boolean includeDates, boolean includeNotes, boolean includeBudget,
        boolean includeStartingPart, boolean includeGarage, List<String> listIds,
        List<String> excludedVehicleIds, List<String> confirmedCustomVehicleIds) {
    public PublicationOptions {
        listIds = bounded(listIds,100); excludedVehicleIds=bounded(excludedVehicleIds,25);
        confirmedCustomVehicleIds=bounded(confirmedCustomVehicleIds,25);
    }
    public PublicationOptions(boolean dates, boolean notes, boolean budget) {
        this(dates,notes,budget,false,false,List.of(),List.of(),List.of());
    }
    @JsonCreator public static PublicationOptions of(
        @JsonProperty("includeDates") Boolean dates, @JsonProperty("includeNotes") Boolean notes,
        @JsonProperty("includeBudget") Boolean budget, @JsonProperty("includeStartingPart") Boolean starting,
        @JsonProperty("includeGarage") Boolean garage, @JsonProperty("listIds") List<String> lists,
        @JsonProperty("excludedVehicleIds") List<String> excluded, @JsonProperty("confirmedCustomVehicleIds") List<String> confirmed) {
        return new PublicationOptions(Boolean.TRUE.equals(dates),Boolean.TRUE.equals(notes),Boolean.TRUE.equals(budget),
            Boolean.TRUE.equals(starting),Boolean.TRUE.equals(garage),lists,excluded,confirmed);
    }
    public static PublicationOptions of(Boolean d,Boolean n,Boolean b){ return new PublicationOptions(Boolean.TRUE.equals(d),Boolean.TRUE.equals(n),Boolean.TRUE.equals(b)); }
    public static PublicationOptions none(){return new PublicationOptions(false,false,false);}
    public static PublicationOptions orNone(PublicationOptions value){return value==null?none():value;}
    /** Never expose the owner's private block/vehicle IDs in the public inclusion summary. */
    public PublicationOptions publicSummary(){return new PublicationOptions(includeDates,includeNotes,includeBudget,includeStartingPart,includeGarage,List.of(),List.of(),List.of());}
    private static List<String> bounded(List<String> values,int limit){
        if(values==null)return List.of();
        if(values.size()>limit||values.stream().anyMatch(v->v==null||v.isBlank()||v.length()>160))throw new IllegalArgumentException("Invalid publication selection");
        return values.stream().distinct().toList();
    }
}
