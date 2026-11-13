package com.navio.tripplanningservice;

import com.navio.tripplanningservice.media.PictureValidator;
import com.navio.tripplanningservice.repository.TripCoverRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Trip covers against real Postgres: owner scoping, replacement, batching and cascade on trip delete. */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "NAVIO_TEST_DB_URL", matches = ".+")
class TripCoverPostgresTests {

    @Autowired TripCoverRepository covers;
    @Autowired JdbcClient db;

    UUID owner;
    UUID trip;

    @BeforeEach
    void seed() {
        owner = UUID.randomUUID();
        trip = UUID.randomUUID();
        db.sql("INSERT INTO trip.trip(id,user_id,display_name,start_date,end_date,destination_id,destination_name) VALUES(:id,:user,'Cover test','2027-01-01','2027-01-02','test','Test')")
                .param("id", trip).param("user", owner).update();
    }

    @AfterEach
    void cleanup() {
        db.sql("DELETE FROM trip.trip WHERE id=:id").param("id", trip).update();
    }

    @Test
    void replacingACoverKeepsOneRowAndOnlyTheOwnerCanReadIt() {
        covers.save(trip, UUID.randomUUID(), new PictureValidator.Picture(new byte[] {1}, "image/png"));
        UUID latest = UUID.randomUUID();
        covers.save(trip, latest, new PictureValidator.Picture(new byte[] {2, 3}, "image/jpeg"));

        assertThat(covers.findCoverId(trip)).contains(latest);
        assertThat(covers.findCoverIds(List.of(trip, UUID.randomUUID()))).containsOnlyKeys(trip).containsEntry(trip, latest);
        assertThat(covers.findOwned(trip, owner)).hasValueSatisfying(picture -> {
            assertThat(picture.bytes()).containsExactly(2, 3);
            assertThat(picture.contentType()).isEqualTo("image/jpeg");
        });
        assertThat(covers.findOwned(trip, UUID.randomUUID())).isEmpty();
        assertThat(db.sql("SELECT version FROM trip.trip WHERE id=:id").param("id", trip).query(Long.class).single()).isZero();
    }

    @Test
    void deletingTheTripDeletesItsCover() {
        covers.save(trip, UUID.randomUUID(), new PictureValidator.Picture(new byte[] {1}, "image/png"));

        db.sql("DELETE FROM trip.trip WHERE id=:id").param("id", trip).update();

        assertThat(db.sql("SELECT count(*) FROM trip.trip_cover_picture WHERE trip_id=:id").param("id", trip).query(Long.class).single()).isZero();
    }

    @Test
    void anEmptyBatchQueriesNothing() {
        assertThat(covers.findCoverIds(List.of())).isEmpty();
    }
}
