package com.navio.tripplanningservice.service;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.navio.tripplanningservice.model.BlockItem;
import com.navio.tripplanningservice.service.PlannerService.PlannerValidationException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LegTravelModesTests {

    private static final JsonNodeFactory JSON = JsonNodeFactory.instance;

    @Test
    void takesTheTrainBetweenTwoStopsGoogleTypesAsStations() {
        assertThat(LegTravelModes.resolve(typed("subway_station"), typed("train_station"))).isEqualTo("transit");
    }

    @Test
    void recognisesAStationSavedBeforeItsTypeWasKeptByItsLabel() {
        assertThat(LegTravelModes.resolve(labelled("Subway Station"), labelled("Train station"))).isEqualTo("transit");
    }

    @Test
    void drivesWhenOnlyOneEndIsAStation() {
        assertThat(LegTravelModes.resolve(typed("subway_station"), typed("museum"))).isEqualTo("drive");
    }

    @Test
    void drivesFromADayAnchorBecauseAnchorsAreNeverStations() {
        assertThat(LegTravelModes.resolve(null, typed("subway_station"))).isEqualTo("drive");
    }

    @Test
    void doesNotMistakeAPetrolStationForATransitStation() {
        assertThat(LegTravelModes.resolve(labelled("Gas Station"), labelled("Gas Station"))).isEqualTo("drive");
    }

    @Test
    void letsTheTravellersChoiceOverrideTheAutomaticMode() {
        BlockItem arrival = typed("subway_station");
        arrival.setArrivalMode("drive");

        assertThat(LegTravelModes.resolve(typed("subway_station"), arrival)).isEqualTo("drive");
    }

    @Test
    void keepsTheSavedModeWhenAnOlderClientOmitsIt() {
        BlockItem item = new BlockItem();
        item.setArrivalMode("walk");

        LegTravelModes.applyArrivalMode(item, "place", null);

        assertThat(item.getArrivalMode()).isEqualTo("walk");
    }

    @Test
    void resetsToAutomaticOnAnExplicitNull() {
        BlockItem item = new BlockItem();
        item.setArrivalMode("walk");

        LegTravelModes.applyArrivalMode(item, "place", JSON.nullNode());

        assertThat(item.getArrivalMode()).isNull();
    }

    @Test
    void rejectsAnUnknownTravelMode() {
        assertThatThrownBy(() -> LegTravelModes.applyArrivalMode(new BlockItem(), "place", JSON.textNode("teleport")))
                .isInstanceOf(PlannerValidationException.class);
    }

    @Test
    void rejectsATravelModeOnANote() {
        assertThatThrownBy(() -> LegTravelModes.applyArrivalMode(new BlockItem(), "note", JSON.textNode("walk")))
                .isInstanceOf(PlannerValidationException.class);
    }

    @Test
    void ignoresAPlaceTypeThatIsNotAPlainIdentifierAndKeepsTheKnownOne() {
        BlockItem item = typed("subway_station");

        LegTravelModes.applyPlaceType(item, "<script>");
        LegTravelModes.applyPlaceType(item, null);

        assertThat(item.getPlaceType()).isEqualTo("subway_station");
    }

    private static BlockItem typed(String placeType) {
        BlockItem item = new BlockItem();
        item.setPlaceType(placeType);
        return item;
    }

    private static BlockItem labelled(String description) {
        BlockItem item = new BlockItem();
        item.setPlaceDescription(description);
        return item;
    }
}
