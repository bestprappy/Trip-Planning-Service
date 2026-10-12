package com.navio.tripplanningservice;

import com.navio.tripplanningservice.dto.PlannerSnapshotRequest;
import com.navio.tripplanningservice.integration.mobility.MobilityEvCharger;
import com.navio.tripplanningservice.integration.mobility.MobilityEvOptimizationResponse;
import com.navio.tripplanningservice.model.*;
import com.navio.tripplanningservice.service.PlannerService;
import com.navio.tripplanningservice.service.TripEvOptimizationApplier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;

@DataJpaTest(properties = {
    "spring.datasource.url=${NAVIO_TEST_DB_URL}",
    "spring.datasource.username=${NAVIO_TEST_DB_USERNAME:tripplanner}",
    "spring.datasource.password=${NAVIO_TEST_DB_PASSWORD:tripplanner}",
    "spring.datasource.driver-class-name=org.postgresql.Driver",
    "spring.flyway.enabled=true", "spring.flyway.schemas=trip", "spring.flyway.default-schema=trip",
    "spring.flyway.create-schemas=true", "spring.jpa.hibernate.ddl-auto=validate"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@EnabledIfEnvironmentVariable(named = "NAVIO_TEST_DB_URL", matches = ".+")
@Import({TripEvOptimizationApplier.class, PlannerService.class, ChargingPlanPersistenceTests.Metrics.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ChargingPlanPersistenceTests {
    @TestConfiguration static class Metrics {
        @Bean io.micrometer.core.instrument.MeterRegistry meterRegistry() {
            return new io.micrometer.core.instrument.simple.SimpleMeterRegistry();
        }
    }
    @Autowired EntityManager em;
    @Autowired PlatformTransactionManager manager;
    @Autowired TripEvOptimizationApplier applier;
    @Autowired PlannerService planner;
    record Fixture(UUID tripId, UUID userId, UUID stopId, long version) {}
    TransactionTemplate tx() { return new TransactionTemplate(manager); }

    Fixture fixture() {
        return tx().execute(status -> {
            var trip = Trip.builder().userId(UUID.randomUUID()).startDate(LocalDate.of(2026,10,10))
                    .endDate(LocalDate.of(2026,10,10)).destinationId("test").destinationName("Test")
                    .visibility(TripVisibility.PRIVATE).build();
            em.persist(trip);
            var day = ListBlock.builder().tripId(trip.getId()).clientId("day").name("Day 1")
                    .type(ListBlock.ListBlockType.ITINERARY).blockDate(trip.getStartDate()).displayOrder(0).build();
            em.persist(day);
            var stop = BlockItem.builder().blockId(day.getId()).clientId("charger").title("Original")
                    .type(BlockItem.BlockItemType.PLACE).displayOrder(0).stationId("station")
                    .placeId("ev-charger:station").placeName("Original").placeLat(13.0).placeLng(100.0)
                    .estimatedChargeMinutes(5).notes("Keep note").build();
            em.persist(stop); em.flush();
            return new Fixture(trip.getId(), trip.getUserId(), stop.getId(), trip.getVersion());
        });
    }
    MobilityEvOptimizationResponse plan() {
        var station = new MobilityEvCharger("station", "Station", "Operator",
                new MobilityEvCharger.Location(13,100,"Address","station"), "Address", null,
                List.of("CCS2"),100,2,null,null,Map.of(),"TEST","VERIFIED","active",0,0,0,false);
        var operation = new MobilityEvOptimizationResponse.Operation("UPDATE_CHARGER","charger",null,0,
                station,5.125,30,47.08333333333333,0,"test");
        return new MobilityEvOptimizationResponse("day",true,List.of(operation),47.08333333333333,0,5.125,"test",List.of());
    }
    @Test void applySaveAndReopenPreservePrecisionAndUserState() throws Exception {
        var f = fixture();
        applier.apply(f.tripId(),f.userId(),"day",f.version(),plan());
        var loaded = planner.getPlannerSnapshot(f.tripId(),f.userId());
        var charge = loaded.blocks().getFirst().items().getFirst().evCharger();
        assertThat(charge.chargingDurationMinutes()).isEqualTo(5.125);
        assertThat(charge.plannedDepartureSocPct()).isEqualTo(47.08333333333333);
        assertThat(charge.targetBatteryPct()).isNull();
        // Exercise the same snapshot save used by autosave, not only entity reload.
        planner.savePlannerSnapshot(f.tripId(),f.userId(),new PlannerSnapshotRequest(loaded.version(),loaded.blocks(),loaded.budget(),loaded.energyState()));
        var reopened = planner.getPlannerSnapshot(f.tripId(),f.userId());
        assertThat(reopened.blocks().getFirst().items().getFirst().evCharger()).isEqualTo(charge);
        assertThat(reopened.blocks().getFirst().items().getFirst().notes()).isEqualTo("Keep note");
        var json = com.fasterxml.jackson.databind.json.JsonMapper.builder().findAndAddModules().build();
        var legacy = json.valueToTree(new PlannerSnapshotRequest(reopened.version(), reopened.blocks(), reopened.budget(), reopened.energyState()));
        var legacyCharge = (com.fasterxml.jackson.databind.node.ObjectNode) legacy.path("blocks").get(0).path("items").get(0).path("evCharger");
        legacyCharge.remove(List.of("chargingDurationMinutes", "plannedDepartureSocPct"));
        planner.savePlannerSnapshot(f.tripId(), f.userId(), json.treeToValue(legacy, PlannerSnapshotRequest.class));
        assertThat(planner.getPlannerSnapshot(f.tripId(), f.userId()).blocks().getFirst().items().getFirst().evCharger()).isEqualTo(charge);
    }
    @Test void rollbackAfterFlushedApplyRestoresEverySavedFieldAndVersion() {
        var f = fixture();
        assertThatThrownBy(() -> tx().execute(status -> {
            applier.apply(f.tripId(),f.userId(),"day",f.version(),plan());
            throw new IllegalStateException("Injected failure after flush");
        })).isInstanceOf(IllegalStateException.class);
        tx().executeWithoutResult(status -> {
            assertThat(em.find(Trip.class,f.tripId()).getVersion()).isEqualTo(f.version());
            var stop = em.find(BlockItem.class,f.stopId());
            assertThat(stop.getTitle()).isEqualTo("Original");
            assertThat(stop.getChargingDurationMinutes()).isNull();
            assertThat(stop.getPlannedDepartureSocPct()).isNull();
            assertThat(stop.getEstimatedChargeMinutes()).isEqualTo(5);
        });
    }
    @Test void concurrentApplicationsCannotBothCommitAtTheSameVersion() throws Exception {
        var f = fixture(); var ready = new CountDownLatch(2);
        try (var executor = Executors.newFixedThreadPool(2)) {
            Callable<Boolean> action = () -> {
                try {
                    tx().executeWithoutResult(status -> {
                        em.find(Trip.class,f.tripId()); // Both persistence contexts see the preview version.
                        ready.countDown();
                        try { if (!ready.await(10,TimeUnit.SECONDS)) throw new IllegalStateException("Barrier timeout"); }
                        catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException(e); }
                        applier.apply(f.tripId(),f.userId(),"day",f.version(),plan());
                    });
                    return true;
                } catch (org.springframework.orm.ObjectOptimisticLockingFailureException expected) { return false; }
            };
            var a = executor.submit(action); var b = executor.submit(action);
            assertThat(List.of(a.get(20,TimeUnit.SECONDS),b.get(20,TimeUnit.SECONDS))).containsExactlyInAnyOrder(true,false);
        }
        assertThat(planner.getPlannerSnapshot(f.tripId(),f.userId()).version()).isEqualTo(f.version()+1);
    }
}
