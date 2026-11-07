package com.navio.tripplanningservice.service.publication;

import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PlanDiscoveryTests {
    private PublicSearchEvidence evidence(String text, Set<String> themes, MonthDay... dates) {
        return new PublicSearchEvidence(text,text,text,text,Set.of("TH"),themes,Set.of(),themes,List.of(dates),2,1);
    }
    @Test void seasonalIsCalendarEvidenceNotClimateOrHiddenDates() {
        var now=LocalDate.of(2026,11,5);
        assertTrue(PlanDiscovery.seasonal(evidence("park",Set.of(),MonthDay.of(11,20)),now));
        assertFalse(PlanDiscovery.seasonal(evidence("winter",Set.of()),now));
        assertFalse(PlanDiscovery.seasonal(evidence("park",Set.of(),MonthDay.of(12,20)),now));
    }
    @Test void eventsRequireOwnerEvidenceAndCompatibleDates() {
        var now=LocalDate.of(2026,12,10);
        assertTrue(PlanDiscovery.event(evidence("christmas holiday",Set.of(),MonthDay.of(12,25)),now));
        assertTrue(PlanDiscovery.event(evidence("christmas holiday",Set.of()),now));
        assertFalse(PlanDiscovery.event(evidence("coastal trip",Set.of(),MonthDay.of(12,25)),now));
        assertFalse(PlanDiscovery.event(evidence("christmas",Set.of(),MonthDay.of(7,25)),now));
        assertFalse(PlanDiscovery.event(evidence("christmas",Set.of()),LocalDate.of(2026,8,1)));
        assertTrue(PlanDiscovery.eventProximity(evidence("christmas",Set.of(),MonthDay.of(12,25)),now)<PlanDiscovery.eventProximity(evidence("christmas",Set.of(),MonthDay.of(12,18)),now));
    }
    @Test void rolloverEasterAndLeapEvidence() {
        assertTrue(PlanDiscovery.event(evidence("new year",Set.of(),MonthDay.of(1,1)),LocalDate.of(2026,12,30)));
        assertEquals(LocalDate.of(2027,3,28),PlanDiscovery.easter(2027));
        assertTrue(PlanDiscovery.event(evidence("easter",Set.of(),MonthDay.of(3,28)),LocalDate.of(2027,3,1)));
        assertTrue(PlanDiscovery.seasonal(evidence("garden",Set.of(),MonthDay.of(2,29)),LocalDate.of(2027,2,1)));
    }
    @Test void halloweenUsesOctoberWindowAndNotAnyAutumnTrip() {
        var now=LocalDate.of(2026,10,12);
        assertTrue(PlanDiscovery.event(evidence("edinburgh halloween",Set.of(),MonthDay.of(10,31)),now));
        assertFalse(PlanDiscovery.event(evidence("edinburgh autumn",Set.of(),MonthDay.of(10,31)),now));
        assertFalse(PlanDiscovery.event(evidence("halloween",Set.of(),MonthDay.of(7,31)),now));
        assertFalse(PlanDiscovery.event(evidence("halloween",Set.of()),LocalDate.of(2026,11,6)));
    }
    @Test void lunarNewYearUsesVerifiedChangingDatesAndCannotMatchGregorianNewYear() {
        assertTrue(PlanDiscovery.event(evidence("chinese new year",Set.of(),MonthDay.of(2,6)),LocalDate.of(2027,1,15)));
        assertTrue(PlanDiscovery.event(evidence("lunar new year",Set.of(),MonthDay.of(1,26)),LocalDate.of(2028,1,10)));
        assertFalse(PlanDiscovery.event(evidence("chinese new year",Set.of(),MonthDay.of(1,1)),LocalDate.of(2026,12,28)));
        assertFalse(PlanDiscovery.event(evidence("lunar new year",Set.of()),LocalDate.of(2032,1,15)));
    }
    @Test void loyKrathongRequiresVerifiedOccurrenceAndExplicitPublicEvidence() {
        assertTrue(PlanDiscovery.event(evidence("chiang mai loy krathong",Set.of(),MonthDay.of(11,24)),LocalDate.of(2026,11,6)));
        assertFalse(PlanDiscovery.event(evidence("chiang mai temples",Set.of(),MonthDay.of(11,24)),LocalDate.of(2026,11,6)));
        assertFalse(PlanDiscovery.event(evidence("loy krathong",Set.of()),LocalDate.of(2027,11,6)));
    }
    @Test void collectionsOrThemesAndRejectUnknown() {
        var filters=PlanDiscovery.Filters.parse("seasonal,events","nature,ev");
        assertTrue(filters.accepts(evidence("forest",Set.of("nature"),MonthDay.of(11,2)),LocalDate.of(2026,11,5)));
        assertFalse(filters.accepts(evidence("forest",Set.of("nature"),MonthDay.of(8,2)),LocalDate.of(2026,11,5)));
        assertFalse(filters.accepts(evidence("museum",Set.of("culture"),MonthDay.of(11,2)),LocalDate.of(2026,11,5)));
        assertThrows(IllegalArgumentException.class,()->PlanDiscovery.Filters.parse("random",""));
    }
    @Test void ratingCopyAndViewSortsUseRealMetricsWithinEligiblePlans(){
        var f=new SearchTestFixtures();
        var a=f.row("a","Temple","Bangkok","Thailand",2);a.setViewCount(100L);a.setRatingCount(1L);a.setRatingSum(3L);
        var b=f.row("b","Forest","Bangkok","Thailand",2);b.setViewCount(10L);b.setRatingCount(2L);b.setRatingSum(10L);
        f.rows(a,b);
        org.mockito.Mockito.when(f.copies.counts(org.mockito.ArgumentMatchers.any())).thenReturn(Map.of(b.getId(),new com.navio.tripplanningservice.repository.SharedPlanCopyRepository.CopyStats(5,3)));
        assertEquals("b",f.service.search("","TH",null,0,6,"rating").matching().getFirst().token());
        assertEquals("b",f.service.search("","TH",null,0,6,"popular").matching().getFirst().token());
        assertEquals("a",f.service.search("","TH",null,0,6,"views").matching().getFirst().token());
        // Lifetime popularity supplies honestly labelled fallback cards, never fabricated activity.
        var discovery=f.service.search("","TH",null,0,6,"trending","trending","");
        assertEquals(2,discovery.matchingTotal());
        assertEquals(Map.of("a","popular","b","popular"),discovery.discoveryLabels());
    }
}
