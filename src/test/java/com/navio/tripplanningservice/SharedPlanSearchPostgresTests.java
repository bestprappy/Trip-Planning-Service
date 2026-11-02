package com.navio.tripplanningservice;
import com.navio.tripplanningservice.repository.ExploreSearchRepository;
import com.navio.tripplanningservice.service.publication.PlanPublicationSanitizer;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;
@DataJpaTest(properties={"spring.datasource.url=${NAVIO_TEST_DB_URL}","spring.datasource.username=${NAVIO_TEST_DB_USERNAME:navio_test}","spring.datasource.password=${NAVIO_TEST_DB_PASSWORD}","spring.datasource.driver-class-name=org.postgresql.Driver","spring.flyway.enabled=true","spring.flyway.locations=classpath:db/migration","spring.flyway.schemas=trip","spring.flyway.default-schema=trip","spring.flyway.create-schemas=true","spring.jpa.hibernate.ddl-auto=validate"})
@AutoConfigureTestDatabase(replace=AutoConfigureTestDatabase.Replace.NONE)
@EnabledIfEnvironmentVariable(named="NAVIO_TEST_DB_URL",matches=".+")
class SharedPlanSearchPostgresTests {
 @Autowired EntityManager em;
 @Autowired ExploreSearchRepository repository;
 @Test void databaseStreamIncludesOnlyCurrentListedActivePublications(){
  UUID owner=UUID.randomUUID();for(int i=0;i<4;i++){
   UUID trip=UUID.randomUUID();em.createNativeQuery("INSERT INTO trip.trip(id,user_id,start_date,end_date,destination_id,destination_name) VALUES(:id,:owner,CURRENT_DATE,CURRENT_DATE,'test','Test')").setParameter("id",trip).setParameter("owner",owner).executeUpdate();
   em.createNativeQuery("INSERT INTO trip.trip_publication(id,trip_id,token,snapshot,sanitizer_version,source_trip_version,listed_in_explore,listed_at,status) VALUES(:id,:trip,:token,CAST('{}' AS jsonb),:version,0,:listed,:at,:status)")
    .setParameter("id",UUID.randomUUID()).setParameter("trip",trip).setParameter("token",i==3?null:"search-test-"+i)
    .setParameter("version",i==2?0:PlanPublicationSanitizer.SANITIZER_VERSION).setParameter("listed",i==0||i==2).setParameter("at",java.time.Instant.now()).setParameter("status",i==3?"REVOKED":"ACTIVE").executeUpdate();
  }
  try(var rows=repository.streamListed(PlanPublicationSanitizer.SANITIZER_VERSION)){assertThat(rows.map(row->row.getToken()).toList()).containsExactly("search-test-0");}
 }
}
