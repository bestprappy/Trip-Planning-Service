package com.navio.tripplanningservice.service;

import com.navio.tripplanningservice.dto.PlannerSnapshotResponse;
import com.navio.tripplanningservice.dto.TripEvOptimizationPreviewResponse;
import com.navio.tripplanningservice.dto.TripEvOptimizationRequest;
import com.navio.tripplanningservice.integration.mobility.MobilityEvCharger;
import com.navio.tripplanningservice.integration.mobility.MobilityEvOptimizationClient;
import com.navio.tripplanningservice.integration.mobility.MobilityEvOptimizationRequest;
import com.navio.tripplanningservice.integration.mobility.MobilityEvOptimizationResponse;
import com.navio.tripplanningservice.model.BlockItem;
import com.navio.tripplanningservice.model.ListBlock;
import com.navio.tripplanningservice.model.Trip;
import com.navio.tripplanningservice.repository.BlockItemRepository;
import com.navio.tripplanningservice.repository.ListBlockRepository;
import com.navio.tripplanningservice.repository.TripRepository;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Service
public class TripEvOptimizationService {

    private static final String CHARGER_PLACE_PREFIX = "ev-charger:";
    private static final String UNKNOWN_CONNECTOR = "OTHER";
    private static final Set<String> SUPPORTED_CONNECTORS = Set.of(
            "CCS1",
            "CCS2",
            "CHADEMO",
            "TYPE2",
            "J1772",
            "NACS",
            "GB_T",
            "OTHER"
    );

    private final TripRepository tripRepository;
    private final ListBlockRepository listBlockRepository;
    private final BlockItemRepository blockItemRepository;
    private final MobilityEvOptimizationClient mobilityClient;
    private final TripEvOptimizationApplier optimizationApplier;
    private final PlannerService plannerService;

    public TripEvOptimizationService(
            TripRepository tripRepository,
            ListBlockRepository listBlockRepository,
            BlockItemRepository blockItemRepository,
            MobilityEvOptimizationClient mobilityClient,
            TripEvOptimizationApplier optimizationApplier,
            PlannerService plannerService
    ) {
        this.tripRepository = tripRepository;
        this.listBlockRepository = listBlockRepository;
        this.blockItemRepository = blockItemRepository;
        this.mobilityClient = mobilityClient;
        this.optimizationApplier = optimizationApplier;
        this.plannerService = plannerService;
    }

    public TripEvOptimizationPreviewResponse preview(
            UUID tripId,
            UUID userId,
            TripEvOptimizationRequest request
    ) {
        Trip trip = requireOwnedTrip(tripId, userId);
        MobilityEvOptimizationResponse response = mobilityClient.optimize(buildMobilityRequest(tripId, request));
        return mapPreview(trip.getVersion(), response);
    }

    public PlannerSnapshotResponse apply(
            UUID tripId,
            UUID userId,
            TripEvOptimizationRequest request
    ) {
        if (request.expectedVersion() == null) {
            throw new TripEvOptimizationException("The optimization preview version is required");
        }
        Trip trip = requireOwnedTrip(tripId, userId);
        if (!Objects.equals(trip.getVersion(), request.expectedVersion())) {
            throw new ObjectOptimisticLockingFailureException(Trip.class, tripId);
        }

        MobilityEvOptimizationResponse response = mobilityClient.optimize(buildMobilityRequest(tripId, request));
        if (!response.feasible()) {
            throw new TripEvOptimizationException(response.message());
        }
        optimizationApplier.apply(
                tripId,
                userId,
                request.blockId(),
                request.expectedVersion(),
                response
        );
        return plannerService.getPlannerSnapshot(tripId, userId);
    }

    private MobilityEvOptimizationRequest buildMobilityRequest(
            UUID tripId,
            TripEvOptimizationRequest request
    ) {
        ListBlock block = listBlockRepository.findByTripIdAndClientId(tripId, request.blockId())
                .orElseThrow(() -> new TripEvOptimizationException("The selected itinerary block was not found"));
        List<MobilityEvOptimizationRequest.Stop> stops = blockItemRepository
                .findByBlockIdOrderByDisplayOrder(block.getId())
                .stream()
                .filter(item -> item.getType() == BlockItem.BlockItemType.PLACE
                        || item.getType() == BlockItem.BlockItemType.EV_STATION)
                .map(this::mapStop)
                .toList();
        if (stops.size() < 2) {
            throw new TripEvOptimizationException(
                    "Add at least a starting place and destination before optimizing the EV route"
            );
        }

        TripEvOptimizationRequest.Vehicle vehicle = request.vehicle();
        return new MobilityEvOptimizationRequest(
                request.blockId(),
                stops,
                new MobilityEvOptimizationRequest.Vehicle(
                        vehicle.batteryKwh(),
                        vehicle.consumptionKwhPer100km(),
                        vehicle.maxAcKw(),
                        vehicle.maxDcKw(),
                        normalizeConnectors(vehicle.connectorTypes())
                ),
                request.startingSocPct(),
                request.effectiveReserveSocPct(),
                request.effectiveTargetSocPct(),
                request.effectiveMaximumDetourKm()
        );
    }

    private MobilityEvOptimizationRequest.Stop mapStop(BlockItem item) {
        if (item.getPlaceLat() == null || item.getPlaceLng() == null) {
            throw new TripEvOptimizationException("Every route place needs coordinates before optimization");
        }
        MobilityEvCharger charger = isCharger(item) ? mapCharger(item) : null;
        return new MobilityEvOptimizationRequest.Stop(
                item.getClientId(),
                Objects.requireNonNullElse(item.getPlaceName(), item.getTitle()),
                item.getPlaceLat(),
                item.getPlaceLng(),
                charger,
                charger != null && Boolean.TRUE.equals(item.getEvLocked()),
                charger == null ? null : Objects.requireNonNullElse(item.getEvSelectionSource(), "MANUAL"),
                item.getTargetBatteryPct()
        );
    }

    /**
     * Maps a saved charger item to its Mobility snapshot, or {@code null} when the snapshot is too
     * incomplete to model. An older or partially saved charger then travels as an ordinary waypoint
     * instead of failing the whole route optimization.
     */
    private MobilityEvCharger mapCharger(BlockItem item) {
        String chargerId = item.getStationId();
        if (chargerId == null && item.getPlaceId() != null && item.getPlaceId().startsWith(CHARGER_PLACE_PREFIX)) {
            chargerId = item.getPlaceId().substring(CHARGER_PLACE_PREFIX.length());
        }
        if (chargerId == null || chargerId.isBlank() || item.getPowerKw() == null || item.getPowerKw() <= 0) {
            return null;
        }
        Map<String, Object> openingHours = item.getOpeningHours() == null
                ? Map.of()
                : Map.of("summary", item.getOpeningHours());
        return new MobilityEvCharger(
                chargerId,
                Objects.requireNonNullElse(item.getPlaceName(), item.getTitle()),
                item.getEvOperatorName(),
                new MobilityEvCharger.Location(
                        item.getPlaceLat(),
                        item.getPlaceLng(),
                        item.getPlaceAddress(),
                        chargerId
                ),
                item.getPlaceAddress(),
                null,
                splitConnectors(item.getEvConnectorTypes()),
                item.getPowerKw(),
                Objects.requireNonNullElse(item.getEvTotalConnectors(), 0),
                item.getEvAvailableConnectors(),
                item.getPrice(),
                openingHours,
                "TRIP_SNAPSHOT",
                "STALE",
                "active",
                Objects.requireNonNullElse(item.getPlaceRating(), 0.0),
                Objects.requireNonNullElse(item.getPlaceReviewCount(), 0),
                0.35,
                true
        );
    }

    private TripEvOptimizationPreviewResponse mapPreview(
            long version,
            MobilityEvOptimizationResponse response
    ) {
        return new TripEvOptimizationPreviewResponse(
                version,
                response.blockId(),
                response.feasible(),
                response.operations().stream().map(this::mapOperation).toList(),
                response.finalSocPct(),
                response.totalDrivingSeconds(),
                response.totalChargingMinutes(),
                response.message(),
                response.warnings()
        );
    }

    private TripEvOptimizationPreviewResponse.Operation mapOperation(
            MobilityEvOptimizationResponse.Operation operation
    ) {
        return new TripEvOptimizationPreviewResponse.Operation(
                operation.type(),
                operation.oldItemId(),
                operation.beforeItemId(),
                operation.sequence(),
                operation.charger() == null ? null : mapCharger(operation.charger()),
                operation.estimatedChargeMinutes(),
                operation.arrivalSocPct(),
                operation.departureSocPct(),
                operation.detourKm(),
                operation.reason()
        );
    }

    private TripEvOptimizationPreviewResponse.Charger mapCharger(MobilityEvCharger charger) {
        return new TripEvOptimizationPreviewResponse.Charger(
                charger.id(),
                charger.name(),
                charger.operatorName(),
                new TripEvOptimizationPreviewResponse.Location(
                        charger.location().lat(),
                        charger.location().lng(),
                        charger.location().address(),
                        charger.location().placeId()
                ),
                charger.address(),
                charger.province(),
                charger.connectorTypes(),
                charger.maxKw(),
                charger.totalConnectors(),
                charger.availableConnectors(),
                charger.priceText(),
                charger.openingHours(),
                charger.source(),
                charger.verificationStatus(),
                charger.status(),
                charger.ratingAvg(),
                charger.ratingCount(),
                charger.confidenceScore(),
                charger.stale()
        );
    }

    private Trip requireOwnedTrip(UUID tripId, UUID userId) {
        return tripRepository.findByIdAndUserId(tripId, userId)
                .orElseThrow(() -> new TripService.TripNotFoundException(tripId));
    }

    private boolean isCharger(BlockItem item) {
        return item.getStationId() != null
                || item.getEvConnectorTypes() != null
                || item.getPlaceId() != null && item.getPlaceId().startsWith(CHARGER_PLACE_PREFIX);
    }

    private List<String> splitConnectors(String value) {
        if (value == null || value.isBlank()) {
            return List.of(UNKNOWN_CONNECTOR);
        }
        return normalizeConnectors(java.util.Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(connector -> !connector.isBlank())
                .toList());
    }

    /**
     * Maps connector names onto the vocabulary Mobility accepts. Anything Mobility cannot parse
     * would be rejected as a malformed body and surface as "optimization is unavailable", so
     * unrecognised names degrade to {@code OTHER} instead.
     */
    private List<String> normalizeConnectors(List<String> connectors) {
        if (connectors == null || connectors.isEmpty()) {
            return List.of(UNKNOWN_CONNECTOR);
        }
        List<String> normalized = connectors.stream()
                .filter(Objects::nonNull)
                .map(connector -> connector.trim().toUpperCase(Locale.ROOT))
                .map(connector -> SUPPORTED_CONNECTORS.contains(connector) ? connector : UNKNOWN_CONNECTOR)
                .distinct()
                .toList();
        return normalized.isEmpty() ? List.of(UNKNOWN_CONNECTOR) : normalized;
    }
}
