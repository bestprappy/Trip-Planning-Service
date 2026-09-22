package com.navio.tripplanningservice.service;

import com.navio.tripplanningservice.integration.mobility.MobilityEvCharger;
import com.navio.tripplanningservice.integration.mobility.MobilityEvOptimizationResponse;
import com.navio.tripplanningservice.model.BlockItem;
import com.navio.tripplanningservice.model.ListBlock;
import com.navio.tripplanningservice.model.Trip;
import com.navio.tripplanningservice.repository.BlockItemRepository;
import com.navio.tripplanningservice.repository.ListBlockRepository;
import com.navio.tripplanningservice.repository.TripRepository;
import com.navio.tripplanningservice.support.BlockItemTextLimits;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import static com.navio.tripplanningservice.support.BlockItemTextLimits.clamp;

@Service
public class TripEvOptimizationApplier {

    private static final String CHARGER_PLACE_PREFIX = "ev-charger:";

    private final TripRepository tripRepository;
    private final ListBlockRepository listBlockRepository;
    private final BlockItemRepository blockItemRepository;

    public TripEvOptimizationApplier(
            TripRepository tripRepository,
            ListBlockRepository listBlockRepository,
            BlockItemRepository blockItemRepository
    ) {
        this.tripRepository = tripRepository;
        this.listBlockRepository = listBlockRepository;
        this.blockItemRepository = blockItemRepository;
    }

    @Transactional
    public long apply(
            UUID tripId,
            UUID userId,
            String blockClientId,
            long expectedVersion,
            MobilityEvOptimizationResponse optimization
    ) {
        Trip trip = tripRepository.findByIdAndUserId(tripId, userId)
                .orElseThrow(() -> new TripService.TripNotFoundException(tripId));
        if (!Objects.equals(trip.getVersion(), expectedVersion)) {
            throw new ObjectOptimisticLockingFailureException(Trip.class, tripId);
        }
        ListBlock block = listBlockRepository.findByTripIdAndClientId(tripId, blockClientId)
                .orElseThrow(() -> new TripEvOptimizationException("The itinerary block no longer exists"));
        List<BlockItem> items = new ArrayList<>(blockItemRepository.findByBlockIdOrderByDisplayOrder(block.getId()));

        List<MobilityEvOptimizationResponse.Operation> removalOperations = optimization.operations().stream()
                .filter(operation -> "REMOVE_CHARGER".equals(operation.type())
                        || "REPLACE_CHARGER".equals(operation.type()))
                .toList();
        for (MobilityEvOptimizationResponse.Operation operation : removalOperations) {
            BlockItem existing = findItem(items, operation.oldItemId());
            if (existing == null) {
                throw new TripEvOptimizationException("A charger changed after the optimization preview");
            }
            if (Boolean.TRUE.equals(existing.getEvLocked()) || existing.getTargetBatteryPct() != null) {
                throw new TripEvOptimizationException("Unlock the charger before replacing it");
            }
            items.remove(existing);
            blockItemRepository.delete(existing);
        }

        for (MobilityEvOptimizationResponse.Operation operation : optimization.operations()) {
            if (!"UPDATE_CHARGER".equals(operation.type())) {
                continue;
            }
            BlockItem existing = findItem(items, operation.oldItemId());
            if (existing != null && operation.charger() != null) {
                applyChargerSnapshot(existing, operation.charger(), operation.estimatedChargeMinutes());
            }
        }

        List<MobilityEvOptimizationResponse.Operation> insertionOperations = optimization.operations().stream()
                .filter(operation -> "ADD_CHARGER".equals(operation.type())
                        || "REPLACE_CHARGER".equals(operation.type()))
                .sorted(Comparator.comparingInt(MobilityEvOptimizationResponse.Operation::sequence))
                .toList();
        for (MobilityEvOptimizationResponse.Operation operation : insertionOperations) {
            if (operation.charger() == null) {
                throw new TripEvOptimizationException("Mobility returned an incomplete charger operation");
            }
            // A charger on the leg into the day's end has no saved item after it, so it goes last.
            int insertionIndex = TripEvOptimizationService.dayEndStopId(block).equals(operation.beforeItemId())
                    ? items.size()
                    : indexOfClientItem(items, operation.beforeItemId());
            if (insertionIndex < 0) {
                throw new TripEvOptimizationException("The charger insertion point no longer exists");
            }
            BlockItem chargerItem = newChargerItem(
                    block.getId(),
                    operation.charger(),
                    operation.estimatedChargeMinutes()
            );
            items.add(insertionIndex, chargerItem);
        }

        for (int index = 0; index < items.size(); index++) {
            items.get(index).setDisplayOrder(index);
        }
        blockItemRepository.saveAll(items);
        blockItemRepository.flush();
        trip.setUpdatedAt(Instant.now());
        Trip savedTrip = tripRepository.saveAndFlush(trip);
        return savedTrip.getVersion();
    }

    private BlockItem newChargerItem(
            UUID blockId,
            MobilityEvCharger charger,
            int estimatedChargeMinutes
    ) {
        BlockItem item = BlockItem.builder()
                .blockId(blockId)
                .clientId("charger-" + UUID.randomUUID())
                .type(BlockItem.BlockItemType.PLACE)
                .title(clamp(charger.name(), BlockItemTextLimits.TITLE))
                .placeId(clamp(CHARGER_PLACE_PREFIX + charger.id(), BlockItemTextLimits.PLACE_ID))
                .placeName(clamp(charger.name(), BlockItemTextLimits.NAME))
                .placeDescription(chargerDescription(charger))
                .placeAddress(clamp(
                        charger.address() != null ? charger.address() : charger.location().address(),
                        BlockItemTextLimits.ADDRESS
                ))
                .placeLat(charger.location().lat())
                .placeLng(charger.location().lng())
                .placeRating(charger.ratingAvg())
                .placeReviewCount(Math.toIntExact(Math.min(Integer.MAX_VALUE, charger.ratingCount())))
                .stationId(clamp(charger.id(), BlockItemTextLimits.STATION_ID))
                .stationName(clamp(charger.name(), BlockItemTextLimits.NAME))
                .stationAddress(clamp(charger.address(), BlockItemTextLimits.ADDRESS))
                .stationLat(charger.location().lat())
                .stationLng(charger.location().lng())
                .evSelectionSource("AUTO")
                .evLocked(false)
                .visited(false)
                .isCharged(false)
                .build();
        applyChargerSnapshot(item, charger, estimatedChargeMinutes);
        return item;
    }

    private void applyChargerSnapshot(
            BlockItem item,
            MobilityEvCharger charger,
            int estimatedChargeMinutes
    ) {
        item.setPlaceId(clamp(CHARGER_PLACE_PREFIX + charger.id(), BlockItemTextLimits.PLACE_ID));
        item.setPlaceName(clamp(charger.name(), BlockItemTextLimits.NAME));
        item.setTitle(clamp(charger.name(), BlockItemTextLimits.TITLE));
        item.setPlaceDescription(chargerDescription(charger));
        item.setPlaceAddress(clamp(
                charger.address() != null ? charger.address() : charger.location().address(),
                BlockItemTextLimits.ADDRESS
        ));
        item.setPlaceLat(charger.location().lat());
        item.setPlaceLng(charger.location().lng());
        item.setPlaceRating(charger.ratingAvg());
        item.setPlaceReviewCount(Math.toIntExact(Math.min(Integer.MAX_VALUE, charger.ratingCount())));
        item.setStationId(clamp(charger.id(), BlockItemTextLimits.STATION_ID));
        item.setStationName(clamp(charger.name(), BlockItemTextLimits.NAME));
        item.setStationAddress(clamp(charger.address(), BlockItemTextLimits.ADDRESS));
        item.setStationLat(charger.location().lat());
        item.setStationLng(charger.location().lng());
        item.setEvConnectorTypes(String.join(",", charger.connectorTypes()));
        item.setPowerKw(charger.maxKw());
        item.setEvTotalConnectors(charger.totalConnectors());
        item.setEvAvailableConnectors(charger.availableConnectors());
        item.setAvailablePlugs(charger.availableConnectors());
        item.setPrice(clamp(charger.priceText(), BlockItemTextLimits.PRICE));
        item.setOpeningHours(clamp(openingHoursSummary(charger), BlockItemTextLimits.OPENING_HOURS));
        item.setEstimatedChargeMinutes(estimatedChargeMinutes);
        item.setEvOperatorName(clamp(charger.operatorName(), BlockItemTextLimits.OPERATOR_NAME));
        if (item.getEvSelectionSource() == null) {
            item.setEvSelectionSource("AUTO");
        }
        if (item.getEvLocked() == null) {
            item.setEvLocked(false);
        }
    }

    private String openingHoursSummary(MobilityEvCharger charger) {
        Object summary = charger.openingHours().get("summary");
        return summary instanceof String value && !value.isBlank() ? value : "Hours not listed";
    }

    private String chargerDescription(MobilityEvCharger charger) {
        return "EV charging station - " + String.join(", ", charger.connectorTypes())
                + " - up to " + Math.round(charger.maxKw()) + " kW";
    }

    private BlockItem findItem(List<BlockItem> items, String clientId) {
        if (clientId == null) {
            return null;
        }
        return items.stream().filter(item -> clientId.equals(item.getClientId())).findFirst().orElse(null);
    }

    private int indexOfClientItem(List<BlockItem> items, String clientId) {
        for (int index = 0; index < items.size(); index++) {
            if (Objects.equals(clientId, items.get(index).getClientId())) {
                return index;
            }
        }
        return -1;
    }
}
