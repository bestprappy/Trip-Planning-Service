package com.navio.tripplanningservice;

import com.fasterxml.jackson.databind.*;
import com.navio.tripplanningservice.service.publication.PlanPublicationSanitizer;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import javax.sql.DataSource;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
@EnabledIfEnvironmentVariable(named="NAVIO_SMART_SEARCH_FIXTURES_ALLOWED",matches="true")
class SmartSearchHttpPostgresTests {
 @Autowired DataSource datasource;
 @Autowired ObjectMapper mapper;
 @LocalServerPort int port;
 final HttpClient http=HttpClient.newHttpClient();
 final List<UUID> ids=new ArrayList<>();
 @BeforeEach void identity()throws Exception{try(var c=datasource.getConnection()){assertThat(c.getMetaData().getURL()).isEqualTo("jdbc:postgresql://localhost:56433/navio_smart_search");try(var s=c.createStatement();var r=s.executeQuery("select current_database()")){r.next();assertThat(r.getString(1)).isEqualTo("navio_smart_search");}}}
 @AfterEach void cleanup()throws Exception{try(var c=datasource.getConnection();var p=c.prepareStatement("delete from trip.trip where id=?")){for(var id:ids){p.setObject(1,id);p.addBatch();}p.executeBatch();}ids.clear();}
 void seed(int count)throws Exception{try(var c=datasource.getConnection()){
  c.setAutoCommit(false);try(var t=c.prepareStatement("insert into trip.trip(id,user_id,start_date,end_date,destination_id,destination_name) values(?,?,CURRENT_DATE,CURRENT_DATE,'smart-search-test','Thailand')");var p=c.prepareStatement("insert into trip.trip_publication(id,trip_id,token,snapshot,sanitizer_version,source_trip_version,listed_in_explore,listed_at,status) values(?,?,?,cast(? as jsonb),?,0,true,CURRENT_TIMESTAMP,'ACTIVE')")){
   for(int i=0;i<count;i++){UUID trip=UUID.randomUUID();ids.add(trip);t.setObject(1,trip);t.setObject(2,UUID.randomUUID());t.addBatch();p.setObject(1,UUID.randomUUID());p.setObject(2,trip);p.setString(3,"smart-http-"+trip);p.setString(4,"{\"sanitizerVersion\":"+PlanPublicationSanitizer.SANITIZER_VERSION+",\"title\":\"Nature escape\",\"destinationCity\":\"Bangkok\",\"destinationCountry\":\"Thailand\",\"dayCount\":3,\"days\":[{\"items\":[{\"type\":\"place\",\"name\":\"Forest and mountain\",\"placeType\":\"national_park\"}]}]}");p.setInt(5,PlanPublicationSanitizer.SANITIZER_VERSION);p.addBatch();}
   t.executeBatch();p.executeBatch();c.commit();
  }catch(Exception e){c.rollback();throw e;}}
 }
 HttpResponse<String> request(String query,String suffix)throws Exception{return http.send(HttpRequest.newBuilder(URI.create("http://localhost:"+port+"/v1/shared-plans/search?term="+URLEncoder.encode(query,StandardCharsets.UTF_8)+suffix)).timeout(java.time.Duration.ofSeconds(15)).build(),HttpResponse.BodyHandlers.ofString());}
 @Test void realHttpVisibilityRevisionsAndNaturalIdeas()throws Exception{
  seed(3);var first=request("Thailand nature like gardens forests mountains","");assertThat(first.statusCode()).isEqualTo(200);assertThat(first.headers().firstValue("cache-control").orElse("")).contains("no-store");var initial=mapper.readTree(first.body());assertThat(initial.path("matchingTotal").asInt()).isGreaterThanOrEqualTo(3);String revision=initial.path("searchAssist").path("catalogueRevision").asText();assertThat(revision).hasSize(64);
  var ideas=mapper.readTree(request("Thailand Christmas Holiday","").body());assertThat(ideas.path("searchAssist").path("suggestions").toString()).contains("Christmas holiday trips in Thailand");
  try(var c=datasource.getConnection();var p=c.prepareStatement("update trip.trip_publication set listed_in_explore=false,revision=revision+1 where trip_id=?")){p.setObject(1,ids.getFirst());p.executeUpdate();}
  var stale=request("Thailand nature like gardens forests mountains","&page=1&catalogueRevision="+revision);assertThat(stale.statusCode()).isEqualTo(409);
  var refreshed=mapper.readTree(request("Thailand nature like gardens forests mountains","").body());assertThat(refreshed.path("matchingTotal").asInt()).isEqualTo(initial.path("matchingTotal").asInt()-1);
  assertThat(request("x".repeat(257),"").statusCode()).isEqualTo(400);
 }
 @Test void measuredOneAndFiveThousandCatalogueRequests()throws Exception{
  for(int target:List.of(1000,5000)){
   seed(target-ids.size());long coldStart=System.nanoTime();assertThat(request("Thailand forests or mountains","").statusCode()).isEqualTo(200);long cold=(System.nanoTime()-coldStart)/1_000_000;
   var samples=Collections.synchronizedList(new ArrayList<Long>());
   try(var pool=Executors.newFixedThreadPool(5)){
    var futures=new ArrayList<Future<?>>();for(int i=0;i<25;i++)futures.add(pool.submit(()->{try{long started=System.nanoTime();var r=request("Thailand forests or mountains","");assertThat(r.statusCode()).isEqualTo(200);assertThat(mapper.readTree(r.body()).path("matchingTotal").asInt()).isGreaterThanOrEqualTo(target);samples.add((System.nanoTime()-started)/1_000_000);}catch(Exception e){throw new RuntimeException(e);}}));for(var f:futures)f.get();
   }
   samples.sort(Long::compare);long p50=samples.get(12),p95=samples.get(23),p99=samples.get(24);System.out.println("SMART_SEARCH_HTTP_BENCH publications="+target+" concurrency=5 n=25 coldMs="+cold+" p50Ms="+p50+" p95Ms="+p95+" p99Ms="+p99);
   // Throughput correctness is mandatory; timings are reported rather than flaky CI assertions.
  }
 }
}
