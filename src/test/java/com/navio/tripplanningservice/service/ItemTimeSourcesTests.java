package com.navio.tripplanningservice.service;

import com.navio.tripplanningservice.model.BlockItem;
import com.navio.tripplanningservice.service.PlannerService.PlannerValidationException;
import org.junit.jupiter.api.Test;

import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ItemTimeSourcesTests {

    private static final LocalTime NINE = LocalTime.of(9, 0);
    private static final LocalTime TEN = LocalTime.of(10, 0);

    @Test
    void storesTheSourceTheClientSends() {
        assertThat(ItemTimeSources.resolve(new BlockItem(), "place", "calculated", NINE, TEN)).isEqualTo("calculated");
        assertThat(ItemTimeSources.resolve(new BlockItem(), "place", "fixed", NINE, null)).isEqualTo("fixed");
    }

    @Test
    void keepsTheSavedSourceWhenAnOlderClientResendsUnchangedTimes() {
        assertThat(ItemTimeSources.resolve(saved("calculated"), "place", null, NINE, TEN)).isEqualTo("calculated");
    }

    @Test
    void forgetsACalculatedSourceWhenAnOlderClientChangesTheTime() {
        assertThat(ItemTimeSources.resolve(saved("calculated"), "place", null, TEN, null)).isNull();
    }

    @Test
    void clearsTheSourceWithTheTimes() {
        assertThat(ItemTimeSources.resolve(saved("fixed"), "place", "fixed", null, null)).isNull();
    }

    @Test
    void ignoresASourceOnItemsThatAreNotPlaces() {
        assertThat(ItemTimeSources.resolve(new BlockItem(), "note", "fixed", NINE, TEN)).isNull();
    }

    @Test
    void rejectsAnUnknownSource() {
        assertThatThrownBy(() -> ItemTimeSources.resolve(new BlockItem(), "place", "guessed", NINE, TEN))
                .isInstanceOf(PlannerValidationException.class)
                .hasMessageContaining("fixed or calculated");
    }

    private static BlockItem saved(String source) {
        BlockItem item = new BlockItem();
        item.setStartTime(NINE);
        item.setEndTime(TEN);
        item.setTimeSource(source);
        return item;
    }
}
