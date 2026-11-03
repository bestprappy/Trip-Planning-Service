package com.navio.tripplanningservice.service.publication;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class PlanIntentRelevanceTests {
    final List<PlanSearchQuery.Location> locations=List.of(new PlanSearchQuery.Location("Thailand","TH",true),new PlanSearchQuery.Location("Hua Hin","TH",false));
    PlanSearchQuery.Match match(String query,String title,String text,String... themes){
        return PlanSearchQuery.parse(query,locations,"").match(new PlanSearchQuery.Evidence(title,text,Set.of("TH"),Set.of(themes)),Map.of("TH","Thailand"));
    }
    @Test void countryAloneCannotRescueMissingChristmasOrFood(){
        assertThat(match("Thailand Christmas trip","Coastal escape","Thailand coast")).isNull();
        assertThat(match("Thailand food trip","Coastal escape","Thailand coast")).isNull();
    }
    @Test void specificActivityCannotRelaxToGenericTheme(){
        assertThat(match("Thailand sushi trip","Market walk","Thailand food market","food")).isNull();
    }
    @Test void explicitChristmasWithCountryIsMatching(){
        assertThat(match("Thailand Christmas trip","Christmas in Bangkok","Thailand Christmas market").strong()).isTrue();
    }
    @Test void unknownAndNegativeModifiersAreNotDropped(){
        assertThat(match("Thailand inventedxyz trip","Coastal escape","Thailand coast")).isNull();
        assertThat(match("Thailand not Christmas","Christmas","Thailand Christmas")).isNull();
    }
    @Test void christmasDatesAreRelevantButNeverInventChristmasActivities(){
        var query=PlanSearchQuery.parse("Thailand Christmas trip",locations,"");
        var near=query.match(new PlanSearchQuery.Evidence("Quiet escape","Thailand coast",Set.of("TH"),Set.of(),List.of(java.time.MonthDay.of(12,25))),Map.of());
        var early=query.match(new PlanSearchQuery.Evidence("Quiet escape","Thailand coast",Set.of("TH"),Set.of(),List.of(java.time.MonthDay.of(12,3))),Map.of());
        assertThat(near).isNotNull();assertThat(near.strong()).isFalse();assertThat(near.reason()).contains("December");
        assertThat(near.score()).isGreaterThan(early.score());
        assertThat(query.match(new PlanSearchQuery.Evidence("Quiet escape","Thailand coast",Set.of("TH"),Set.of(),List.of(java.time.MonthDay.of(7,25))),Map.of())).isNull();
    }
    @Test void allOtherRequirementsStillApplyToTemporalAlternative(){
        var query=PlanSearchQuery.parse("Thailand Christmas sushi",locations,"");
        assertThat(query.match(new PlanSearchQuery.Evidence("Coast","Thailand beach",Set.of("TH"),Set.of(),List.of(java.time.MonthDay.of(12,25))),Map.of())).isNull();
    }
    @Test void yearlessPlanCannotClaimToVerifyAnExplicitYear(){
        assertThat(match("Thailand Christmas 2026","Christmas","Thailand Christmas")).isNull();
    }
    @Test void anExplicitPublicMonthTitleCanMatchWithoutPrivateDates(){
        var result=match("Thailand December trip","December escape","Thailand December escape");
        assertThat(result).isNotNull();assertThat(result.strong()).isTrue();
    }

}
