package com.navio.tripplanningservice.service.publication;

import com.navio.tripplanningservice.repository.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TrendingFallbackTests {
    private static final Map<String,SharedPlanSearchService.SearchPage> RESPONSES=new java.util.concurrent.ConcurrentHashMap<>();
    @org.junit.jupiter.api.AfterAll static void exportBrowserFixtures() throws Exception {
        var mapper=new com.fasterxml.jackson.databind.ObjectMapper().findAndRegisterModules()
            .disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        java.nio.file.Files.writeString(java.nio.file.Path.of("target/trending-browser-responses.json"),mapper.writeValueAsString(RESPONSES));
    }
    @Test void emptyActivityFallsBackToPopularThenRecentWithoutInventingActivity() {
        var f=new SearchTestFixtures();
        var popular=f.row("popular","Bangkok museums","Bangkok","Thailand",2);popular.setViewCount(20L);
        var copied=f.row("copied","Bangkok temples","Bangkok","Thailand",2);
        var recent=f.row("recent","Bangkok gardens","Bangkok","Thailand",2);recent.setListedAt(Instant.parse("2026-11-01T00:00:00Z"));
        f.rows(recent,popular,copied);
        when(f.copies.counts(any())).thenReturn(Map.of(copied.getId(),new SharedPlanCopyRepository.CopyStats(2,1)));
        var result=f.service.search("","TH",2,0,6,"trending","trending","");
        RESPONSES.put("noActivity",result);
        assertEquals(List.of("copied","popular","recent"),result.matching().stream().map(p->p.token()).toList());
        assertEquals(3,result.matchingTotal());
        // Metadata is additive and labels fallback cards honestly.
        assertEquals("popular",result.discoveryLabels().get("copied"));
        assertEquals("recent",result.discoveryLabels().get("recent"));
    }
    @Test void sparseActivityComesFirstAndPaginationNeverDuplicatesOrDropsFallbacks() {
        var f=new SearchTestFixtures();
        var rows=java.util.stream.IntStream.range(0,9).mapToObj(i->{var r=f.row("p"+i,"Bangkok museum","Bangkok","Thailand",2);r.setViewCount((long)i);return r;}).toList();
        f.rows(rows.toArray(com.navio.tripplanningservice.model.TripPublication[]::new));
        var activity=mock(PublicationActivityRepository.class);ReflectionTestUtils.setField(f.service,"activity",activity);
        when(activity.scores(any())).thenReturn(Map.of(rows.get(0).getId(),5L,rows.get(1).getId(),10L));
        var first=f.service.search("","TH",2,0,6,"trending","trending","");
        var second=f.service.search("","TH",2,1,6,"trending","trending","");
        RESPONSES.put("sparseFirst",first);RESPONSES.put("sparseSecond",second);
        assertEquals(List.of("p1","p0","p8","p7","p6","p5"),first.matching().stream().map(p->p.token()).toList());
        assertEquals(List.of("p4","p3","p2"),second.matching().stream().map(p->p.token()).toList());
        assertFalse(first.matchingLast());assertTrue(second.matchingLast());
        assertEquals(first.searchAssist().catalogueRevision(),second.searchAssist().catalogueRevision());
    }
    @Test void fallbacksNeverRelaxCountryDurationThemeOrQuery() {
        var f=new SearchTestFixtures();
        var good=f.withPlaces(f.row("good","Bangkok food","Bangkok","Thailand",2),"restaurant","Thai restaurant","Bangkok, Thailand");
        var wrongCountry=f.row("jp","Tokyo food","Tokyo","Japan",2);wrongCountry.setViewCount(999L);
        var wrongDays=f.row("days","Bangkok food","Bangkok","Thailand",3);wrongDays.setViewCount(999L);
        var wrongTheme=f.row("theme","Bangkok museum","Bangkok","Thailand",2);wrongTheme.setViewCount(999L);
        f.rows(good,wrongCountry,wrongDays,wrongTheme);
        assertEquals(List.of("good"),f.service.search("food","TH",2,0,6,"trending","trending","food").matching().stream().map(p->p.token()).toList());
        var empty=f.service.search("","US",2,0,6,"trending","trending","");
        RESPONSES.put("empty",empty);assertEquals(0,empty.matchingTotal());
        assertEquals(0,f.service.search("unicorn spaceship","TH",2,0,6,"trending","trending","").matchingTotal());
    }
    @Test void explicitSortsApplyWithinTiersAndOtherCollectionsDoNotGainFallbackLabels() {
        var f=new SearchTestFixtures();var a=f.row("a","Temple","Bangkok","Thailand",2);a.setViewCount(5L);
        var b=f.row("b","Temple","Bangkok","Thailand",2);b.setViewCount(9L);f.rows(a,b);
        assertEquals("b",f.service.search("","",null,0,6,"views","trending","").matching().getFirst().token());
        assertTrue(f.service.search("","",null,0,6,"recent","","").discoveryLabels().isEmpty());
    }
    @Test void mixedTiersKeepLeadersFirstAndRespectExplicitSecondaryPriority() {
        var f=new SearchTestFixtures();
        var active=f.row("active","Temple","Bangkok","Thailand",2);active.setViewCount(1L);
        var rated=f.row("rated","Temple","Bangkok","Thailand",2);rated.setViewCount(5L);rated.setRatingSum(5L);rated.setRatingCount(1L);
        var popular=f.row("popular","Temple","Bangkok","Thailand",2);popular.setViewCount(99L);popular.setRatingSum(1L);popular.setRatingCount(1L);
        var recent=f.row("recent","Temple","Bangkok","Thailand",2);recent.setListedAt(Instant.parse("2026-11-01T00:00:00Z"));
        f.rows(recent,popular,rated,active);
        var activity=mock(PublicationActivityRepository.class);ReflectionTestUtils.setField(f.service,"activity",activity);
        when(activity.scores(any())).thenReturn(Map.of(active.getId(),1L));
        assertEquals(List.of("active","rated","popular","recent"),f.service.search("","",null,0,6,"trending,rating,views","trending","").matching().stream().map(p->p.token()).toList());
        assertEquals("trending",f.service.search("","",null,0,6,"recent","trending","").discoveryLabels().get("active"));
    }
}
