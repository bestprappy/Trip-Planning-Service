package com.navio.tripplanningservice.service;

import com.navio.tripplanningservice.dto.PlannerScheduleEntryDto;
import com.navio.tripplanningservice.model.BlockItem;
import com.navio.tripplanningservice.service.PlannerService.PlannerValidationException;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ItemSchedulesTests {

    private static final PlannerScheduleEntryDto CHECK_IN = new PlannerScheduleEntryDto("a", "09:00", "09:10", "Check in");
    private static final PlannerScheduleEntryDto SHOWER = new PlannerScheduleEntryDto("b", "09:30", null, "อาบน้ำ");

    @Test
    void storesAndReadsBackTheTravellersMoments() {
        BlockItem item = new BlockItem();
        ItemSchedules.apply(item, "place", List.of(CHECK_IN, SHOWER));
        assertThat(ItemSchedules.read(item)).containsExactly(CHECK_IN, SHOWER);
    }

    @Test
    void keepsTheSavedScheduleWhenAnOlderClientOmitsIt() {
        BlockItem item = withSchedule(CHECK_IN);
        ItemSchedules.apply(item, "place", null);
        assertThat(ItemSchedules.read(item)).containsExactly(CHECK_IN);
    }

    @Test
    void clearsTheScheduleWhenTheClientSendsNoMoments() {
        BlockItem item = withSchedule(CHECK_IN);
        ItemSchedules.apply(item, "place", List.of());
        assertThat(item.getSchedule()).isNull();
        assertThat(ItemSchedules.read(item)).isNull();
    }

    @Test
    void dropsAScheduleFromItemsThatAreNotPlaces() {
        BlockItem item = withSchedule(CHECK_IN);
        ItemSchedules.apply(item, "note", List.of(SHOWER));
        assertThat(item.getSchedule()).isNull();
    }

    @Test
    void rejectsAStartTimeThatIsNotHoursAndMinutes() {
        var loose = new PlannerScheduleEntryDto("a", "9:00", null, "Check in");
        assertThatThrownBy(() -> ItemSchedules.apply(new BlockItem(), "place", List.of(loose)))
                .isInstanceOf(PlannerValidationException.class).hasMessageContaining("HH:mm");
    }

    @Test
    void rejectsAnEndTimeOutsideTheDay() {
        var badEnd = new PlannerScheduleEntryDto("a", "09:00", "24:00", "Check in");
        assertThatThrownBy(() -> ItemSchedules.apply(new BlockItem(), "place", List.of(badEnd)))
                .isInstanceOf(PlannerValidationException.class).hasMessageContaining("HH:mm");
    }

    @Test
    void rejectsTwoMomentsWithTheSameId() {
        var copy = new PlannerScheduleEntryDto("a", "10:00", null, "Leave");
        assertThatThrownBy(() -> ItemSchedules.apply(new BlockItem(), "place", List.of(CHECK_IN, copy)))
                .isInstanceOf(PlannerValidationException.class).hasMessageContaining("id");
    }

    @Test
    void rejectsMoreMomentsThanAStopCanHold() {
        List<PlannerScheduleEntryDto> tooMany = IntStream.rangeClosed(0, ItemSchedules.MAX_ENTRIES)
                .mapToObj(index -> new PlannerScheduleEntryDto("m" + index, "09:00", null, "Step"))
                .toList();
        assertThatThrownBy(() -> ItemSchedules.apply(new BlockItem(), "place", tooMany))
                .isInstanceOf(PlannerValidationException.class).hasMessageContaining("at most");
    }

    @Test
    void rejectsALabelLongerThanTheLimit() {
        var wordy = new PlannerScheduleEntryDto("a", "09:00", null,
                String.join("", Collections.nCopies(ItemSchedules.MAX_LABEL_LENGTH + 1, "x")));
        assertThatThrownBy(() -> ItemSchedules.apply(new BlockItem(), "place", List.of(wordy)))
                .isInstanceOf(PlannerValidationException.class).hasMessageContaining("label");
    }

    @Test
    void ignoresAStoredScheduleItCannotRead() {
        BlockItem item = new BlockItem();
        item.setSchedule("not json");
        assertThat(ItemSchedules.read(item)).isNull();
    }

    private static BlockItem withSchedule(PlannerScheduleEntryDto... entries) {
        BlockItem item = new BlockItem();
        ItemSchedules.apply(item, "place", List.of(entries));
        return item;
    }
}
