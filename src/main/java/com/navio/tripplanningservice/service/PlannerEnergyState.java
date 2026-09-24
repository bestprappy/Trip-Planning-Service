package com.navio.tripplanningservice.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.navio.tripplanningservice.model.Trip;
import com.navio.tripplanningservice.model.BlockItem;
import java.math.BigDecimal;
import java.util.Map;
import java.util.Set;
import com.navio.tripplanningservice.service.PlannerService.PlannerValidationException;

/** Omitted properties preserve existing state; explicit JSON null clears it. */
final class PlannerEnergyState {
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private PlannerEnergyState() {}
    static void apply(Trip trip, JsonNode state) {
        if (state == null) return;
        if (state.isNull()) { trip.setInitialSocPct(null); trip.setEnergyVehicleSnapshot(null); trip.setGarageVehicleIds(null); return; }
        if (!state.isObject()) fail("Invalid trip energy state");
        if (state.has("garageVehicleIds")) {
            var ids = state.get("garageVehicleIds");
            if (!ids.isArray() || ids.size() > 25) fail("Trip garage must contain at most 25 vehicle IDs");
            var values = new java.util.ArrayList<String>();
            for (var id : ids) {
                if (!id.isTextual() || !id.asText().matches("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")) fail("Invalid trip vehicle ID");
                if (values.contains(id.asText())) fail("Duplicate trip vehicle ID");
                values.add(id.asText());
            }
            trip.setGarageVehicleIds(values);
        }
        if (state.has("initialSocPct")) trip.setInitialSocPct(soc(state.get("initialSocPct")));
        if (state.has("vehicleSnapshot")) {
            JsonNode snapshot = state.get("vehicleSnapshot");
            if (snapshot.isNull()) trip.setEnergyVehicleSnapshot(null);
            else {
                validateSnapshot(snapshot);
                trip.setEnergyVehicleSnapshot(JSON.convertValue(snapshot, new com.fasterxml.jackson.core.type.TypeReference<Map<String,Object>>() {}));
            }
        }
    }
    static void applyObservation(BlockItem item, String type, JsonNode observation) {
        if (observation == null) return;
        if (observation.isNull()) { item.setObservedSocPct(null); return; }
        if (!"place".equals(type) || !observation.isObject() || !observation.has("socPct") || observation.get("socPct").isNull()) fail("A battery observation requires a place and SoC");
        item.setObservedSocPct(soc(observation.get("socPct")));
    }
    static BigDecimal soc(JsonNode value) {
        if (value.isNull()) return null;
        if (!value.isNumber() || !Double.isFinite(value.doubleValue()) || value.doubleValue() < 0 || value.doubleValue() > 100) fail("SoC must be between 0 and 100");
        if (value.decimalValue().stripTrailingZeros().scale() > 2) fail("SoC supports at most two decimal places");
        return value.decimalValue();
    }
    private static void validateSnapshot(JsonNode s) {
        fields(s, Set.of("version", "vehicleId", "label", "profile", "maxAcKw", "maxDcKw", "connectorTypes", "legacyConsumptionConfirmed"));
        if (!s.isObject() || s.path("version").asInt() != 1 || !s.path("vehicleId").isTextual() || s.path("vehicleId").asText().isBlank()
                || !s.path("label").isTextual() || s.path("label").asText().length() > 240 || !s.path("connectorTypes").isArray()
                || !s.path("legacyConsumptionConfirmed").isBoolean()) fail("Invalid vehicle calculation snapshot");
        var p = s.path("profile");
        fields(p, Set.of("version", "modelKind", "selectionMode", "consumptionKwhPer100km", "consumptionSource", "consumptionMeasurementBasis", "consumptionStandard", "sourceUrl", "usableBatteryCapacityKwh", "capacityBasis", "ratedRangeKm", "ratedRangeStandard"));
        if (s.path("vehicleId").asText().length() > 160 || s.path("connectorTypes").size() > 8) fail("Invalid snapshot identity or connectors");
        var source = p.get("sourceUrl");
        if (source == null || (!source.isNull() && (!source.isTextual() || !source.asText().startsWith("https://") || source.asText().length() > 2048))) fail("Invalid consumption source URL");
        if (!p.isObject() || p.path("version").asInt() != 1) fail("Invalid energy profile version");
        member(p,"modelKind",Set.of("CONSUMPTION","RATED_RANGE","UNAVAILABLE"));
        member(p,"selectionMode",Set.of("CATALOG_DEFAULT","USER_OVERRIDE","LEGACY_UNCONFIRMED"));
        member(p,"consumptionSource",Set.of("USER_OBSERVED","MANUFACTURER_REPORTED","REGULATORY_REPORTED","UNKNOWN"));
        member(p,"consumptionMeasurementBasis",Set.of("BATTERY_SIDE","WALL_SIDE","TRIP_COMPUTER","UNKNOWN"));
        member(p,"capacityBasis",Set.of("USABLE","GROSS","MANUFACTURER_DECLARED_UNSPECIFIED","UNKNOWN"));
        var standards = Set.of("NEDC","WLTP","EPA","CLTC","OTHER","NONE");
        member(p,"consumptionStandard",standards); member(p,"ratedRangeStandard",standards);
        for (var field : new String[]{"consumptionKwhPer100km","usableBatteryCapacityKwh","ratedRangeKm"}) nullableNumber(p,field,true);
        nullableNumber(s,"maxAcKw",false); nullableNumber(s,"maxDcKw",false);
        if ("RATED_RANGE".equals(p.path("modelKind").asText()) && !p.path("consumptionKwhPer100km").isNull()) fail("Rated range must not contain consumption");
        for (var connector : s.path("connectorTypes")) if (!Set.of("CCS1","CCS2","CHADEMO","NACS","GB_T","TYPE2","J1772","OTHER").contains(connector.asText())) fail("Invalid connector");
        if (s.toString().length() > 12000) fail("Vehicle snapshot is too large");
    }
    private static void member(JsonNode node, String field, Set<String> values) {
        if (!node.path(field).isTextual() || !values.contains(node.path(field).asText())) fail("Invalid " + field);
    }
    private static void fields(JsonNode node, Set<String> allowed) {
        node.fieldNames().forEachRemaining(field -> { if (!allowed.contains(field)) fail("Unexpected snapshot field: " + field); });
    }
    private static void nullableNumber(JsonNode node,String field,boolean positive) {
        var n=node.get(field);
        if(n==null || (!n.isNull() && (!n.isNumber() || !Double.isFinite(n.doubleValue()) || (positive ? n.doubleValue()<=0 : n.doubleValue()<0)))) fail("Invalid " + field);
    }
    static JsonNode read(Trip trip) {
        var node=JsonNodeFactory.instance.objectNode();
        if(trip.getInitialSocPct()==null) node.putNull("initialSocPct"); else node.put("initialSocPct",trip.getInitialSocPct());
        node.set("vehicleSnapshot",JSON.valueToTree(trip.getEnergyVehicleSnapshot()));
        if (trip.getGarageVehicleIds()!=null) node.set("garageVehicleIds", JSON.valueToTree(trip.getGarageVehicleIds()));
        return trip.getInitialSocPct()==null && trip.getEnergyVehicleSnapshot()==null && trip.getGarageVehicleIds()==null ? JsonNodeFactory.instance.nullNode() : node;
    }
    static JsonNode readObservation(BlockItem item) {
        return item.getObservedSocPct()==null ? JsonNodeFactory.instance.nullNode() : JsonNodeFactory.instance.objectNode().put("socPct",item.getObservedSocPct());
    }
    private static void fail(String message) { throw new PlannerValidationException(message); }
}
