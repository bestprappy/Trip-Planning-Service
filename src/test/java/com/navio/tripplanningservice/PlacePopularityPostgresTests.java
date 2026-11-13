package com.navio.tripplanningservice;

import com.navio.tripplanningservice.repository.PlacePopularityRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(properties = {
        "spring.datasource.url=${NAVIO_TEST_DB_URL}",
        "spring.datasource.username=${NAVIO_TEST_DB_USERNAME:tripplanner}",
        "spring.datasource.password=${NAVIO_TEST_DB_PASSWORD:tripplanner}",
        "spring.datasource.driver-class-name=org.postgresql.Driver",
        "spring.flyway.enabled=true", "spring.flyway.locations=classpath:db/migration",
        "spring.flyway.schemas=trip", "spring.flyway.default-schema=trip", "spring.flyway.create-schemas=true",
        "spring.jpa.hibernate.ddl-auto=validate"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@EnabledIfEnvironmentVariable(named = "NAVIO_TEST_DB_URL", matches = ".+")
class PlacePopularityPostgresTests {
    @Autowired javax.sql.DataSource dataSource;
    JdbcClient jdbc;
    PlacePopularityRepository repository;
    final UUID team = UUID.fromString("0ae736e4-a315-456e-bb2e-7257d419b2d9");

    @BeforeEach void setup() {
        jdbc = JdbcClient.create(dataSource);
        repository = new PlacePopularityRepository(jdbc);
    }

    UUID add(UUID user, String place, String blockType, String client, double lat, double lng, String placeType) {
        UUID trip = UUID.randomUUID(), block = UUID.randomUUID();
        jdbc.sql("INSERT INTO trip.trip(id,user_id,display_name,start_date,end_date,destination_id,destination_name,visibility) VALUES (:id,:user,'Private trip',CURRENT_DATE,CURRENT_DATE,'bangkok','Bangkok','PRIVATE')")
                .param("id", trip).param("user", user).update();
        jdbc.sql("INSERT INTO trip.list_block(id,trip_id,client_id,name,type,display_order,block_date) VALUES (:id,:trip,:client,'Day',:type,0,CURRENT_DATE)")
                .param("id", block).param("trip", trip).param("client", "day-"+block).param("type", blockType).update();
        jdbc.sql("INSERT INTO trip.block_item(id,block_id,client_id,type,display_order,title,place_id,place_name,place_lat,place_lng,place_type) VALUES (:id,:block,:client,'PLACE',0,'Private custom label',:place,'Private custom label',:lat,:lng,:kind)")
                .param("id", UUID.randomUUID()).param("block", block).param("client", client).param("place", place)
                .param("lat", lat).param("lng", lng).param("kind", placeType).update();
        return trip;
    }

    void add(UUID user, String place) { add(user, place, "ITINERARY", "place-"+UUID.randomUUID(),13.75,100.5,"tourist_attraction"); }
    List<PlacePopularityRepository.Count> counts() { return repository.nearby(13.75,100.5,5,List.of(team)); }

    @Test void countsDistinctTravelersAcrossPrivateTripsAndCanonicalizesLegacyGoogleIds() {
        for (int i=0;i<5;i++) {
            UUID user = UUID.randomUUID();
            add(user, "GOOGLE:ChIJplaceA"); add(user, "ChIJplaceA"); add(user, "GOOGLE:ChIJplaceB");
        }
        add(UUID.randomUUID(), "GOOGLE:ChIJplaceA");
        assertThat(counts()).containsExactly(new PlacePopularityRepository.Count("GOOGLE:ChIJplaceA",6),
                new PlacePopularityRepository.Count("GOOGLE:ChIJplaceB",5));
    }

    @Test void optOutWithdrawsExistingContributionsAndOptInRestoresOneVote() {
        UUID user = UUID.randomUUID();
        for (int i=0;i<4;i++) add(UUID.randomUUID(),"GOOGLE:ChIJplaceA");
        add(user,"GOOGLE:ChIJplaceA"); add(user,"GOOGLE:ChIJplaceA");
        assertThat(repository.contributes(user)).isTrue(); assertThat(counts()).hasSize(1);
        repository.setContributes(user,false); repository.setContributes(user,false);
        assertThat(repository.contributes(user)).isFalse(); assertThat(counts()).isEmpty();
        repository.setContributes(user,true);
        assertThat(counts()).containsExactly(new PlacePopularityRepository.Count("GOOGLE:ChIJplaceA",5));
        assertThat(jdbc.sql("SELECT count(*) FROM trip.place_popularity_preference WHERE user_id=:id").param("id",user).query(Long.class).single()).isEqualTo(1);
    }

    @Test void excludesSeedsMyListPersonalLocationsInheritedCopiesResidentialAndFarAwayStops() {
        for (int i=0;i<5;i++) {
            add(team,"GOOGLE:ChIJteam");
            UUID user=UUID.randomUUID();
            add(user,"GOOGLE:ChIJlist","LIST","place-"+i,13.75,100.5,"restaurant");
            add(user,"GOOGLE:ChIJcopy","ITINERARY","copied-item-"+i,13.75,100.5,"restaurant");
            add(user,"SAVED_LOCATION:home","ITINERARY","place-"+i,13.75,100.5,"restaurant");
            add(user,"GOOGLE:ChIJhome","ITINERARY","place-"+i,13.75,100.5,"street_address");
            add(user,"GOOGLE:ChIJfar","ITINERARY","place-"+i,35.6,139.7,"tourist_attraction");
            add(user,"GOOGLE:ChIJnorth","ITINERARY","place-"+i,14.19,100.5,"tourist_attraction");
        }
        assertThat(counts()).containsExactly(new PlacePopularityRepository.Count("GOOGLE:ChIJnorth",5));
    }

    @Test void removingLastRetainedOccurrenceWithdrawsTheTravelersVote() {
        UUID user=UUID.randomUUID();
        for(int i=0;i<4;i++) add(UUID.randomUUID(),"GOOGLE:ChIJplaceA");
        add(user,"GOOGLE:ChIJplaceA"); add(user,"GOOGLE:ChIJplaceA");
        UUID block=jdbc.sql("SELECT b.id FROM trip.list_block b JOIN trip.trip t ON t.id=b.trip_id WHERE t.user_id=:user LIMIT 1").param("user",user).query(UUID.class).single();
        jdbc.sql("DELETE FROM trip.block_item WHERE block_id=:id").param("id",block).update();
        assertThat(counts()).hasSize(1);
        jdbc.sql("DELETE FROM trip.block_item WHERE block_id IN (SELECT b.id FROM trip.list_block b JOIN trip.trip t ON t.id=b.trip_id WHERE t.user_id=:user)").param("user",user).update();
        assertThat(counts()).isEmpty();
    }

    UUID publish(String place, boolean listed, String status) {
        UUID trip=add(UUID.randomUUID(),place,"ITINERARY","place-"+UUID.randomUUID(),13.75,100.5,"restaurant");
        UUID publication=UUID.randomUUID();
        String snapshot="{\"days\":[{\"items\":[{\"type\":\"place\",\"placeId\":\""+place+"\",\"name\":\"Public catalogue name\",\"lat\":13.75,\"lng\":100.5}]}]}";
        jdbc.sql("INSERT INTO trip.trip_publication(id,trip_id,token,snapshot,sanitizer_version,source_trip_version,listed_in_explore,listed_at,status) VALUES (:id,:trip,:token,CAST(:snapshot AS jsonb),1,0,:listed,CURRENT_TIMESTAMP,:status)")
                .param("id",publication).param("trip",trip).param("token",status.equals("ACTIVE")?"public-"+publication:null).param("snapshot",snapshot)
                .param("listed",listed && status.equals("ACTIVE")).param("status",status).update();
        return publication;
    }

    @Test void publicMetadataComesOnlyFromActiveExplicitlyListedPlansAndVanishingListingsAreNotCached() {
        UUID publicId=publish("ChIJpublic",true,"ACTIVE");
        publish("GOOGLE:unlisted",false,"ACTIVE"); publish("GOOGLE:revoked",true,"REVOKED");
        var sources=repository.publicSources(List.of("GOOGLE:ChIJpublic","GOOGLE:unlisted","GOOGLE:revoked"),1);
        assertThat(sources.getFirst().placeId()).isEqualTo("GOOGLE:ChIJpublic");
        assertThat(sources).hasSize(1); assertThat(sources.getFirst().json()).contains("Public catalogue name").doesNotContain("Private custom label");
        jdbc.sql("UPDATE trip.trip_publication SET listed_in_explore=false WHERE id=:id").param("id",publicId).update();
        assertThat(repository.publicSources(List.of("GOOGLE:ChIJpublic"),1)).isEmpty();
    }
}
