package com.navio.tripplanningservice;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import com.navio.tripplanningservice.integration.mobility.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import java.net.URI;
import java.net.http.*;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/** Real HTTP + PostgreSQL; only the external geography provider is substituted. */
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
@EnabledIfEnvironmentVariable(named="NAVIO_TEST_DB_URL",matches=".+")
class SharedPlanRoundTripPostgresTests {
 @Value("${local.server.port}") int port;
 @MockitoBean MobilityPlaceClient places;
 final ObjectMapper json=new ObjectMapper();
 final HttpClient http=HttpClient.newHttpClient();
 @Test void allCompleteExamplesPublishCopyAndReloadWithoutMissingSelectedSections() throws Exception {
  when(places.getDetail(anyString())).thenReturn(new MobilityPlaceDetail("Kyoto",new MobilityPlaceDetail.Coordinates(35.01,135.76),new MobilityPlaceDetail.PlaceLocation("Kyoto","Kyoto","JP","Japan")));
  var fixture=json.readTree(getClass().getResourceAsStream("/shared-plan-review.json"));
  for(var sample:fixture){
   String owner=UUID.randomUUID().toString(),copier=UUID.randomUUID().toString();
   var plan=sample.path("shared").path("plan");
   var create=json.createObjectNode().put("displayName",sample.path("label").asText()).put("startDate",plan.path("startDate").asText()).put("endDate",plan.path("endDate").asText()).put("destinationId","review-kyoto");
   var trip=request("POST","/v1/trips",owner,create,201);String id=trip.path("id").asText();
   var source=(ObjectNode)sample.path("source").deepCopy();source.remove("options");source.put("version",trip.path("version").asLong());
   var saved=request("PUT","/v1/trips/"+id+"/planner",owner,source,200);
   var sourceReload=request("GET","/v1/trips/"+id+"/planner",owner,null,200);
   assertThat(sourceReload.path("energyState").path("vehicles").size()).isEqualTo(plan.path("garage").size());
   var publication=json.createObjectNode().put("expectedTripVersion",saved.path("version").asLong()).put("listInExplore",true).put("authorDisplayName","Navio review example").put("title",sample.path("label").asText());publication.set("options",sample.path("source").path("options"));
   var published=request("PUT","/v1/trips/"+id+"/publication",owner,publication,200);String token=published.path("token").asText();
   var publicPlan=request("GET","/v1/shared-plans/"+token,null,null,200).path("plan");
   assertThat(publicPlan.path("startingPart").path("content").asText()).isEqualTo(plan.path("startingPart").path("content").asText());
   assertThat(publicPlan.path("lists").size()).isEqualTo(plan.path("lists").size());
   assertThat(publicPlan.path("garage").size()).isEqualTo(plan.path("garage").size());
   for(var originalVehicle:sample.path("source").path("energyState").path("vehicles"))assertThat(publicPlan.toString()).doesNotContain(originalVehicle.path("vehicle").path("id").asText());
   for(String mode:new String[]{"retain","change"}){
    var choice=json.createObjectNode().put("dateMode",mode).put("startDate","2030-01-01");
    var copied=request("POST","/v1/shared-plans/"+token+"/copies",copier,choice,201);
    var reload=request("GET","/v1/trips/"+copied.path("id").asText()+"/planner",copier,null,200);
    assertThat(copied.path("startDate").asText()).isEqualTo("retain".equals(mode)?plan.path("startDate").asText():"2030-01-01");
    assertThat(reload.path("blocks").size()).isEqualTo(source.path("blocks").size());
    assertThat(reload.path("blocks").get(0).path("id").asText()).startsWith("starting-part-");
    assertThat(reload.path("blocks").get(0).path("items").get(0).path("content").asText()).isEqualTo(plan.path("startingPart").path("content").asText());
    assertThat(reload.path("blocks").get(1).path("title").asText()).isEqualTo(plan.path("lists").get(0).path("title").asText());
    assertThat(reload.path("blocks").get(1).path("items").get(1).path("items").size()).isEqualTo(3);
    var firstDay=reload.path("blocks").get(2);
    assertThat(firstDay.path("title").asText()).isEqualTo(plan.path("days").get(0).path("title").asText());
    assertThat(firstDay.path("colorId").asText()).isEqualTo("teal");
    assertThat(firstDay.path("items").get(0).path("schedule").size()).isEqualTo(2);
    assertThat(reload.path("energyState").path("vehicles").size()).isEqualTo(publicPlan.path("garage").size());
    for(var vehicle:reload.path("energyState").path("vehicles"))assertThat(vehicle.path("scope").asText()).isEqualTo("trip");
    assertThat(reload.path("budget").path("expenses").size()).isEqualTo(3);
    assertThat(reload.path("budget").path("expenses").get(2).path("id").asText()).startsWith("place-expense-"+firstDay.path("items").get(0).path("id").asText()+"/");
   }
  }
 }
 private JsonNode request(String method,String path,String user,JsonNode body,int status)throws Exception{
  var builder=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+path)).timeout(java.time.Duration.ofSeconds(20)).header("Content-Type","application/json");
  if(user!=null)builder.header("X-User-Id",user);
  builder.method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(body.toString()));
  var response=http.send(builder.build(),HttpResponse.BodyHandlers.ofString());
  assertThat(response.statusCode()).as(method+" "+path+" "+response.body()).isEqualTo(status);
  return json.readTree(response.body());
 }
}
