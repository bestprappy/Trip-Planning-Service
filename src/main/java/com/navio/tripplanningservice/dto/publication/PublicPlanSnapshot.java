package com.navio.tripplanningservice.dto.publication;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDate;
import java.util.List;

/**
 * The whole of what a recipient receives.
 *
 * <p>This is the stored, frozen artefact: the sanitiser runs once at publish
 * time and the result is what every anonymous read serves. There is no owner id,
 * no trip id, no account identifier and no version of the private snapshot in
 * it — a reader cannot work back to the original trip, and revoking the link
 * removes the only thing that pointed at this object.
 *
 * @param sanitizerVersion the {@code PlanPublicationSanitizer} version that
 *                         produced this snapshot. Read-time checks refuse to
 *                         serve a snapshot built by an older sanitiser, so a
 *                         redaction fix cannot be defeated by rows frozen
 *                         before it landed
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PublicPlanSnapshot(
        int sanitizerVersion,
        String title,
        String destinationCity,
        String destinationCountry,
        LocalDate startDate,
        LocalDate endDate,
        int dayCount,
        List<PublicDayDto> days,
        PublicBudgetDto budget,
        PublicationOptions included,
        PublicStartingPartDto startingPart,
        List<PublicDayDto> lists,
        List<com.fasterxml.jackson.databind.JsonNode> garage
) {
    public PublicPlanSnapshot(int version,String title,String city,String country,LocalDate start,LocalDate end,int count,
        List<PublicDayDto> days,PublicBudgetDto budget,PublicationOptions included){
        this(version,title,city,country,start,end,count,days,budget,included,null,null,null);
    }
}
