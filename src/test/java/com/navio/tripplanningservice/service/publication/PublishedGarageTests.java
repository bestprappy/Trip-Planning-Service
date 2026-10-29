package com.navio.tripplanningservice.service.publication;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.navio.tripplanningservice.dto.publication.PublicationOptions;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
class PublishedGarageTests {
 private final ObjectMapper json=new ObjectMapper();
 @Test void customVehiclesNeedConfirmationAndCopiesReceiveTripScopedIdentities() throws Exception {
  String id="11111111-1111-4111-8111-111111111111";
  var energy=json.readTree("""
   {"initialSocPct":70,"garageVehicleIds":["11111111-1111-4111-8111-111111111111"],"vehicleSnapshot":{"vehicleId":"11111111-1111-4111-8111-111111111111"},"vehicles":[{"scope":"account","vehicle":{
    "id":"11111111-1111-4111-8111-111111111111","make":"Example","model":"EV","nickname":"Touring car","year":2026,
    "batteryCapacityKwh":60,"rangeKm":400,"consumptionKwhPer100km":16,"connectorTypes":["CCS2"],"catalog":null,
    "isDefault":true,"createdAt":"2026-10-01T00:00:00Z","updatedAt":"2026-10-01T00:00:00Z",
    "settings":{"maxAcKw":11,"maxDcKw":100,"startingBatteryPct":80,"imageUrl":null},
    "energyProfile":{"version":1,"modelKind":"CONSUMPTION","selectionMode":"USER_OVERRIDE","consumptionKwhPer100km":16,
     "consumptionSource":"USER_OBSERVED","consumptionMeasurementBasis":"UNKNOWN","consumptionStandard":"NONE","sourceUrl":null,
     "usableBatteryCapacityKwh":null,"capacityBasis":"UNKNOWN","ratedRangeKm":400,"ratedRangeStandard":"NONE"}}}]}
   """);
  var unconfirmed=new PublicationOptions(false,false,false,false,true,List.of(),List.of(),List.of());
  assertThat(PublishedGarage.publish(energy,unconfirmed)).isNull();
  var options=new PublicationOptions(false,false,false,false,true,List.of(),List.of(),List.of(id));
  var published=PublishedGarage.publish(energy,options);assertThat(published).hasSize(1);
  assertThat(published.toString()).doesNotContain(id,"createdAt","isDefault");
  var copied=PublishedGarage.copy(published);assertThat(copied.path("vehicles").get(0).path("scope").asText()).isEqualTo("trip");
  assertThat(copied.path("vehicleSnapshot").path("vehicleId").asText()).isNotEqualTo(id);
  assertThat(copied.path("initialSocPct").asInt()).isEqualTo(70);
  assertThat(copied.path("vehicleSnapshot").path("profile").path("consumptionKwhPer100km").asInt()).isEqualTo(16);
  ((ObjectNode)energy).putArray("garageVehicleIds");
  assertThat(PublishedGarage.publish(energy,options)).isNull();
 }
}
