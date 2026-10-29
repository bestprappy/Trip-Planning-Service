package com.navio.tripplanningservice.service;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.navio.tripplanningservice.model.Trip;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class PlannerTripVehiclesTests {
 @Test void explicitlyEmptyVehiclesPersistAndOlderSavesPreserveThem() throws Exception {
  var trip=new Trip();var mapper=new ObjectMapper();
  PlannerEnergyState.apply(trip,mapper.readTree("{\"vehicles\":[]} " ));
  assertThat(PlannerEnergyState.read(trip).path("vehicles").isArray()).isTrue();
  PlannerEnergyState.apply(trip,mapper.readTree("{\"initialSocPct\":50}"));
  assertThat(PlannerEnergyState.read(trip).path("vehicles").isArray()).isTrue();
 }
 @Test void oversizedAndForeignVehicleFieldsAreRejected(){
  var mapper=new ObjectMapper();var root=mapper.createObjectNode();var arr=root.putArray("vehicles");
  arr.addObject().put("scope","trip").putObject("vehicle").put("id","private-owner-id").put("ownerId","secret");
  assertThatThrownBy(()->PlannerEnergyState.apply(new Trip(),root)).isInstanceOf(PlannerService.PlannerValidationException.class);
 }
}
