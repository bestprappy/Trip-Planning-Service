package com.navio.tripplanningservice.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import java.util.Set;

/** Bounds persisted trip-local descriptors. Account references never become public identities. */
public final class TripVehicleDescriptors {
 private TripVehicleDescriptors(){}
 private static final Set<String> VEHICLE_FIELDS=Set.of("id","nickname","make","model","year","batteryCapacityKwh","rangeKm","consumptionKwhPer100km","connectorTypes","isDefault","createdAt","updatedAt","settings","catalog","energyProfile","legacyConsumptionConfirmed");
 public static JsonNode validate(JsonNode entries){
  if(!entries.isArray()||entries.size()>25||entries.toString().length()>250000)fail("Invalid trip vehicle collection");
  var ids=new java.util.HashSet<String>();
  for(var entry:entries){
   fields(entry,Set.of("scope","vehicle"));
   if(!Set.of("account","trip").contains(entry.path("scope").asText()))fail("Invalid vehicle scope");
   var v=entry.path("vehicle");fields(v,VEHICLE_FIELDS);
   try{java.util.UUID.fromString(v.path("id").asText());}catch(IllegalArgumentException e){fail("Invalid vehicle identity");}
   if(!ids.add(v.path("id").asText()))fail("Duplicate trip vehicle");
   for(var f:new String[]{"make","model"})if(!v.path(f).isTextual()||v.path(f).asText().isBlank()||v.path(f).asText().length()>100)fail("Invalid vehicle name");
   if(v.path("nickname").asText().length()>100)fail("Invalid vehicle nickname");
   for(var f:new String[]{"batteryCapacityKwh","rangeKm"})if(!v.path(f).isNumber()||!Double.isFinite(v.path(f).doubleValue())||v.path(f).doubleValue()<=0||v.path(f).doubleValue()>999999.99)fail("Invalid vehicle specification");
   var settings=v.path("settings");fields(settings,Set.of("maxAcKw","maxDcKw","startingBatteryPct","imageUrl"));
   if(!settings.path("startingBatteryPct").canConvertToInt()||settings.path("startingBatteryPct").asInt()<0||settings.path("startingBatteryPct").asInt()>100)fail("Invalid vehicle battery setting");
   String image=settings.path("imageUrl").isNull()?"":settings.path("imageUrl").asText();
   if(!image.isEmpty()&&!image.matches("/images/vehicles/[a-z0-9-]+\\.(png|webp)")&&!safeHttps(image))fail("Invalid vehicle image");
   if(image.length()>2048)fail("Vehicle image too long");
   if(!v.path("year").isNull()&&(!v.path("year").canConvertToInt()||v.path("year").asInt()<1900||v.path("year").asInt()>2200))fail("Invalid vehicle year");
   for(var dateField:new String[]{"createdAt","updatedAt"})try{java.time.Instant.parse(v.path(dateField).asText());}catch(java.time.format.DateTimeParseException e){fail("Invalid vehicle timestamp");}
   var catalog=v.get("catalog");
   if(catalog!=null&&!catalog.isNull())fields(catalog,Set.of("id","make","model","trim","year","market","batteryCapacityKwh","batteryCapacityBasis","rangeKm","rangeStandard","connectorTypes","maxAcKw","maxDcKw","imageUrl","sourceUrl","verifiedAt","version","energyProfile"));
   if(catalog!=null&&!catalog.isNull()){
    for(var field:new String[]{"id","make","model","trim","market","imageUrl","sourceUrl","verifiedAt","batteryCapacityBasis","rangeStandard"})if(!catalog.path(field).isTextual())fail("Invalid catalogue descriptor");
    if(!safeHttps(catalog.path("sourceUrl").asText()))fail("Invalid catalogue source URL");
    String catalogImage=catalog.path("imageUrl").asText();if(!catalogImage.isEmpty()&&!catalogImage.startsWith("/images/vehicles/")&&!safeHttps(catalogImage))fail("Invalid catalogue image URL");
    for(var field:new String[]{"rangeKm","batteryCapacityKwh"})if(!catalog.path(field).isNumber()||catalog.path(field).doubleValue()<=0||!Double.isFinite(catalog.path(field).doubleValue()))fail("Invalid catalogue specification");
    try{java.time.LocalDate.parse(catalog.path("verifiedAt").asText());}catch(java.time.format.DateTimeParseException e){fail("Invalid catalogue verification date");}
   }
   if(v.path("energyProfile").hasNonNull("sourceUrl")&&!safeHttps(v.path("energyProfile").path("sourceUrl").asText()))fail("Invalid energy source URL");
   var snapshot=JsonNodeFactory.instance.objectNode().put("version",1).put("vehicleId",v.path("id").asText()).put("label",v.path("make").asText()+" "+v.path("model").asText()).put("legacyConsumptionConfirmed",v.path("legacyConsumptionConfirmed").asBoolean(false));
   snapshot.set("profile",v.path("energyProfile"));snapshot.set("maxAcKw",settings.path("maxAcKw"));snapshot.set("maxDcKw",settings.path("maxDcKw"));snapshot.set("connectorTypes",v.path("connectorTypes"));
   PlannerEnergyState.validateSnapshot(snapshot);
  }
  return entries.deepCopy();
 }
 private static boolean safeHttps(String value){try{var uri=java.net.URI.create(value);return value.length()<=2048&&"https".equals(uri.getScheme())&&uri.getHost()!=null&&uri.getUserInfo()==null;}catch(IllegalArgumentException e){return false;}}
 private static void fields(JsonNode n,Set<String> allowed){if(!n.isObject())fail("Invalid vehicle object");n.fieldNames().forEachRemaining(k->{if(!allowed.contains(k))fail("Unexpected vehicle field");});}
 private static void fail(String message){throw new PlannerService.PlannerValidationException(message);}
}
