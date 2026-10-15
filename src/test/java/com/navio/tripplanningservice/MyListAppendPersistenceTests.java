package com.navio.tripplanningservice;

import com.navio.tripplanningservice.dto.*;
import com.navio.tripplanningservice.integration.mobility.*;
import com.navio.tripplanningservice.model.*;
import com.navio.tripplanningservice.service.*;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.*;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.transaction.*;
import org.springframework.transaction.annotation.*;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;

/** Opt in only with a disposable, migrated PostgreSQL test database, never production. */
@DataJpaTest(properties = {
        "spring.config.location=optional:classpath:append-test.yml",
        "spring.datasource.url=${NAVIO_APPEND_TEST_DB_URL}",
        "spring.datasource.username=${NAVIO_TEST_DB_USERNAME:tripplanner}",
        "spring.datasource.password=${NAVIO_TEST_DB_PASSWORD:tripplanner}",
        "spring.datasource.driver-class-name=org.postgresql.Driver",
        "spring.flyway.enabled=true", "spring.flyway.schemas=trip", "spring.flyway.default-schema=trip",
        "spring.flyway.create-schemas=true", "spring.jpa.hibernate.ddl-auto=validate"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@EnabledIfEnvironmentVariable(named = "NAVIO_APPEND_TEST_DB_URL", matches = ".+")
@Import({MyListPlaceService.class, PlannerService.class, MyListAppendPersistenceTests.Dependencies.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class MyListAppendPersistenceTests {
    @TestConfiguration static class Dependencies {
        @Bean MobilityPlaceClient places() {
            return id -> new MobilityPlaceDetail("Test garden", new MobilityPlaceDetail.Coordinates(13.7, 100.5), null);
        }
        @Bean io.micrometer.core.instrument.MeterRegistry metrics() {
            return new io.micrometer.core.instrument.simple.SimpleMeterRegistry();
        }
    }
    @Autowired MyListPlaceService append;
    @Autowired PlannerService planner;
    @Autowired EntityManager em;
    @Autowired PlatformTransactionManager manager;
    UUID tripId, userId;
    long version;

    @BeforeEach void fixture() {
        new TransactionTemplate(manager).executeWithoutResult(status -> {
            Trip trip = Trip.builder().userId(UUID.randomUUID()).displayName("Preserve fixture")
                    .destinationId("fixture").destinationName("Fixture")
                    .startDate(LocalDate.of(2026, 12, 1)).endDate(LocalDate.of(2026, 12, 1))
                    .visibility(TripVisibility.PRIVATE).build();
            em.persist(trip); em.flush(); tripId = trip.getId(); userId = trip.getUserId(); version = trip.getVersion();
        });
    }
    @AfterEach void cleanup() {
        if (tripId != null) new TransactionTemplate(manager).executeWithoutResult(status ->
                em.createNativeQuery("delete from trip.trip where id = :id").setParameter("id", tripId).executeUpdate());
    }
    AppendMyListPlaceRequest request() { return new AppendMyListPlaceRequest("fixture-google-place", "GOOGLE", null, version); }

    @Test void simultaneousDuplicateClicksCreateExactlyOneItemAndOneRevision() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Callable<AppendMyListPlaceResponse> operation = () -> { start.await(); return append.append(tripId, userId, request()); };
            Future<AppendMyListPlaceResponse> one = pool.submit(operation), two = pool.submit(operation);
            start.countDown();
            var first = one.get(30, TimeUnit.SECONDS); var second = two.get(30, TimeUnit.SECONDS);
            assertThat(first.itemId()).isEqualTo(second.itemId());
            assertThat(first.alreadyAdded()).isNotEqualTo(second.alreadyAdded());
            var saved = planner.getPlannerSnapshot(tripId, userId);
            assertThat(saved.version()).isEqualTo(version + 1);
            assertThat(saved.blocks()).hasSize(1);
            assertThat(saved.blocks().getFirst().items()).hasSize(1);
        } finally { pool.shutdownNow(); }
    }
    @Test void oldWholeSnapshotCannotEraseAppendedPlace() {
        var added = append.append(tripId, userId, request());
        assertThatThrownBy(() -> planner.savePlannerSnapshot(tripId, userId, new PlannerSnapshotRequest(version, List.of())))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);
        var saved = planner.getPlannerSnapshot(tripId, userId);
        assertThat(saved.version()).isEqualTo(added.version());
        assertThat(saved.blocks().getFirst().items().getFirst().id()).isEqualTo(added.itemId());
    }
    @Test void competingSnapshotAndAppendSerializeWithoutDeadlock() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Callable<Boolean> snapshot = () -> {
                start.await();
                try { planner.savePlannerSnapshot(tripId, userId, new PlannerSnapshotRequest(version, List.of())); return true; }
                catch (ObjectOptimisticLockingFailureException conflict) { return false; }
            };
            Callable<Boolean> addition = () -> {
                start.await();
                try { append.append(tripId, userId, request()); return true; }
                catch (ObjectOptimisticLockingFailureException conflict) { return false; }
            };
            Future<Boolean> one = pool.submit(snapshot), two = pool.submit(addition); start.countDown();
            assertThat(one.get(30, TimeUnit.SECONDS)).isNotEqualTo(two.get(30, TimeUnit.SECONDS));
            assertThat(planner.getPlannerSnapshot(tripId, userId).version()).isEqualTo(version + 1);
        } finally { pool.shutdownNow(); }
    }
}
