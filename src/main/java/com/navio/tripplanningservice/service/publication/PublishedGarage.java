package com.navio.tripplanningservice.service.publication;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.navio.tripplanningservice.dto.publication.PublicationOptions;
import java.util.List;

/** Frozen vehicle content; account IDs and personal progress never appear in public JSON. */
final class PublishedGarage {
 private PublishedGarage(){}
 static List<JsonNode> publish(JsonNode energy,PublicationOptions options){
  if(!options.includeGarage()||energy==null||!energy.path("vehicles").isArray())return null;
  var result=new java.util.ArrayList<JsonNode>();
  for(var entry:energy.path("vehicles")){
   var v=entry.path("vehicle");String id=v.path("id").asText();boolean catalog=v.hasNonNull("catalog");
   boolean selected=energy.path("garageVehicleIds").isArray()?java.util.stream.StreamSupport.stream(energy.path("garageVehicleIds").spliterator(),false).anyMatch(value->id.equals(value.asText())):id.equals(energy.path("vehicleSnapshot").path("vehicleId").asText());
   if(!selected)continue;
   if(options.excludedVehicleIds().contains(id)||(!catalog&&!options.confirmedCustomVehicleIds().contains(id)))continue;
   ObjectNode descriptor=v.deepCopy();descriptor.remove(List.of("id","createdAt","updatedAt","isDefault"));
   var published=JsonNodeFactory.instance.objectNode().put("key","vehicle-"+result.size()).put("source",catalog?"catalog":"custom").put("active",id.equals(energy.path("vehicleSnapshot").path("vehicleId").asText()));
   if(published.path("active").asBoolean())published.set("initialSocPct",energy.has("initialSocPct")?energy.get("initialSocPct"):JsonNodeFactory.instance.nullNode());
   published.set("vehicle",descriptor);result.add(published);
  }
  return result.isEmpty()?null:List.copyOf(result);
 }
 static JsonNode copy(List<JsonNode> garage){
  if(garage==null||garage.isEmpty())return null;
  var root=JsonNodeFactory.instance.objectNode();var entries=root.putArray("vehicles");var ids=root.putArray("garageVehicleIds");
  ObjectNode active=null;JsonNode activeSource=null;
  for(var publicVehicle:garage){
   ObjectNode v=publicVehicle.path("vehicle").deepCopy();String id=java.util.UUID.randomUUID().toString();
   v.put("id",id).put("isDefault",false).put("createdAt",java.time.Instant.now().toString()).put("updatedAt",java.time.Instant.now().toString());
   var entry=entries.addObject().put("scope","trip");entry.set("vehicle",v);ids.add(id);
   if(active==null||publicVehicle.path("active").asBoolean()){active=v;activeSource=publicVehicle;}
  }
  com.navio.tripplanningservice.service.TripVehicleDescriptors.validate(entries);
  if(active!=null){
   if(activeSource!=null&&activeSource.has("initialSocPct"))root.set("initialSocPct",activeSource.get("initialSocPct"));else root.put("initialSocPct",active.path("settings").path("startingBatteryPct").asInt());
   var snapshot=root.putObject("vehicleSnapshot").put("version",1).put("vehicleId",active.path("id").asText()).put("label",active.path("nickname").isTextual()?active.path("nickname").asText():active.path("make").asText()+" "+active.path("model").asText()).put("legacyConsumptionConfirmed",active.path("legacyConsumptionConfirmed").asBoolean());
   snapshot.set("profile",active.path("energyProfile"));snapshot.set("connectorTypes",active.path("connectorTypes"));snapshot.set("maxAcKw",active.path("settings").path("maxAcKw"));snapshot.set("maxDcKw",active.path("settings").path("maxDcKw"));
  }
  return root;
 }
}
