package com.navio.tripplanningservice.service.publication;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.navio.tripplanningservice.dto.publication.*;
import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

class YearlessPublicationTests {
    final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    @Test void publicStructuredDatesNeverSerializeOriginalYear() throws Exception {
        var day = new PublicDayDto("Day 1", LocalDate.of(2032,2,29),null,null,null,null,List.of());
        var plan = new PublicPlanSnapshot(7,"Leap trip","Bangkok","Thailand",LocalDate.of(2032,2,29),LocalDate.of(2032,3,1),2,List.of(day),null,null);
        var json = mapper.writeValueAsString(plan);
        assertThat(json).doesNotContain("2032");
        assertThat(mapper.readTree(json).path("startDate").asText()).isEqualTo("02-29");
    }
    @Test void legacyNewYearDatesKeepOffsetsWithoutExposingYear() throws Exception {
        var plan=PublicPlanDates.read(mapper,"""
          {"sanitizerVersion":1,"dayCount":2,"startDate":"2026-12-31","endDate":"2027-01-03",
           "included":{"includeDates":true},"days":[{"label":"Day 1","date":"2026-12-31"},{"label":"Day 2","date":"2027-01-03"}]}
          """);
        assertThat(plan.daySpan()).isEqualTo(3);
        assertThat(plan.days().get(1).dayOffset()).isEqualTo(3);
        assertThat(mapper.writeValueAsString(plan)).doesNotContain("2026","2027");
        var again=PublicPlanDates.read(mapper,mapper.writeValueAsString(plan));
        assertThat(again.days().get(1).dayOffset()).isEqualTo(3);
        assertThat(again.startDate()).isEqualTo(java.time.MonthDay.of(12,31));
    }
    @Test void hiddenLegacyDatesAreRemovedEvenWhenStaleSnapshotContainsThem() throws Exception {
        var plan=PublicPlanDates.read(mapper,"""
          {"sanitizerVersion":1,"dayCount":1,"startDate":"2026-12-25","included":{"includeDates":false},"days":[{"date":"2026-12-25","title":"2026-12-25","dayOffset":0}]}
          """);
        assertThat(plan.startDate()).isNull();
        assertThat(plan.days().getFirst().date()).isNull();
        assertThat(plan.days().getFirst().dayOffset()).isNull();
        assertThat(mapper.writeValueAsString(plan)).doesNotContain("12-25");
    }
    @Test void invalidMonthDayIsRejectedRatherThanSilentlyNormalized() {
        assertThatThrownBy(()->PublicPlanDates.read(mapper,"{\"sanitizerVersion\":1,\"startDate\":\"02-30\"}")).isInstanceOf(com.fasterxml.jackson.core.JsonProcessingException.class);
    }
    @Test void legacyGeneratedTitleDoesNotRevealDatesWhenDateFieldWasAlreadyAbsent() throws Exception {
        var plan=PublicPlanDates.read(mapper,"{\"sanitizerVersion\":1,\"included\":{\"includeDates\":false},\"days\":[{\"title\":\"2026-12-25\"}]}");
        assertThat(mapper.writeValueAsString(plan)).doesNotContain("2026","12-25");
    }

    @Test void malformedSnapshotRootFailsAsAHandledJsonError() {
        assertThatThrownBy(()->PublicPlanDates.read(mapper,"[]")).isInstanceOf(com.fasterxml.jackson.core.JsonProcessingException.class);
    }

}
