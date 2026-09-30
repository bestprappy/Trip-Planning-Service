package com.navio.tripplanningservice;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Runs every migration against a disposable PostgreSQL, then starts JPA with
 * {@code ddl-auto=validate} exactly as production does. A failing context here is
 * the startup crash production would have. Skipped unless NAVIO_TEST_DB_URL is set;
 * see docs/agents/database-changes.md.
 */
@DataJpaTest(properties = {
        "spring.datasource.url=${NAVIO_TEST_DB_URL}",
        "spring.datasource.username=${NAVIO_TEST_DB_USERNAME:tripplanner}",
        "spring.datasource.password=${NAVIO_TEST_DB_PASSWORD:tripplanner}",
        "spring.datasource.driver-class-name=org.postgresql.Driver",
        "spring.flyway.enabled=true",
        "spring.flyway.locations=classpath:db/migration",
        "spring.flyway.schemas=trip",
        "spring.flyway.default-schema=trip",
        "spring.flyway.create-schemas=true",
        "spring.jpa.hibernate.ddl-auto=validate"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@EnabledIfEnvironmentVariable(named = "NAVIO_TEST_DB_URL", matches = ".+")
class PostgresSchemaTests {

    @Autowired
    Flyway flyway;
    @Autowired jakarta.persistence.EntityManager entityManager;

    @Test
    void migrationsApplyAndEntityMappingsMatchThePostgresSchema() {
        assertThat(flyway.info().pending()).isEmpty();
    }

    @Test
    void publishedSchemaUpgradesWithoutReplacingPublishedMigrations() throws Exception {
        String baseUrl = System.getenv("NAVIO_TEST_DB_URL");
        String username = System.getenv().getOrDefault("NAVIO_TEST_DB_USERNAME", "tripplanner");
        String password = System.getenv().getOrDefault("NAVIO_TEST_DB_PASSWORD", "tripplanner");
        String database = "navio_upgrade_" + java.util.UUID.randomUUID().toString().replace("-", "");
        String upgradeUrl = baseUrl.substring(0, baseUrl.lastIndexOf('/') + 1) + database;
        try (var admin = java.sql.DriverManager.getConnection(baseUrl, username, password);
             var statement = admin.createStatement()) {
            statement.execute("CREATE DATABASE " + database);
            try {
                Flyway.configure().dataSource(upgradeUrl, username, password)
                    .schemas("trip").defaultSchema("trip").target("13").load().migrate();
                try (var connection = java.sql.DriverManager.getConnection(upgradeUrl, username, password);
                     var sql = connection.createStatement()) {
                    sql.execute("INSERT INTO trip.trip (id, user_id, display_name, start_date, end_date, destination_id, destination_name) VALUES ('11111111-1111-4111-8111-111111111111', '22222222-2222-4222-8222-222222222222', 'Preserved trip', CURRENT_DATE, CURRENT_DATE, 'test', 'Test')");
                }
                var upgraded = Flyway.configure().dataSource(upgradeUrl, username, password)
                    .schemas("trip").defaultSchema("trip").load();
                assertThat(upgraded.migrate().migrationsExecuted).isEqualTo(2);
                upgraded.validate();
                try (var connection = java.sql.DriverManager.getConnection(upgradeUrl, username, password);
                     var sql = connection.createStatement();
                     var row = sql.executeQuery("SELECT display_name, initial_soc_pct, energy_vehicle_snapshot, garage_vehicle_ids FROM trip.trip WHERE id = '11111111-1111-4111-8111-111111111111'")) {
                    assertThat(row.next()).isTrue();
                    assertThat(row.getString(1)).isEqualTo("Preserved trip");
                    assertThat(row.getObject(2)).isNull();
                    assertThat(row.getObject(3)).isNull();
                    assertThat(row.getObject(4)).isNull();
                }
            } finally {
                statement.execute("DROP DATABASE " + database);
            }
        }
    }

    @Test
    void tripEnergyAndObservedZeroSurviveRealPostgresReloadAndClear() {
        var trip = com.navio.tripplanningservice.model.Trip.builder()
            .userId(java.util.UUID.randomUUID()).startDate(java.time.LocalDate.of(2026,9,23)).endDate(java.time.LocalDate.of(2026,9,24))
            .destinationId("test").destinationName("Test").visibility(com.navio.tripplanningservice.model.TripVisibility.PRIVATE)
            .initialSocPct(new java.math.BigDecimal("72.25"))
            .garageVehicleIds(java.util.List.of("11111111-1111-4111-8111-111111111111"))
            .energyVehicleSnapshot(java.util.Map.of("version",1,"profile",java.util.Map.of("modelKind","RATED_RANGE","ratedRangeKm",480))).build();
        entityManager.persist(trip);
        var block = com.navio.tripplanningservice.model.ListBlock.builder().tripId(trip.getId()).clientId("day")
            .name("Day 1").type(com.navio.tripplanningservice.model.ListBlock.ListBlockType.ITINERARY).displayOrder(0).blockDate(trip.getStartDate()).build();
        entityManager.persist(block);
        var stop = com.navio.tripplanningservice.model.BlockItem.builder().blockId(block.getId()).clientId("stop")
            .type(com.navio.tripplanningservice.model.BlockItem.BlockItemType.PLACE).displayOrder(0).title("Stop")
            .observedSocPct(java.math.BigDecimal.ZERO).build();
        entityManager.persist(stop); entityManager.flush(); entityManager.clear();
        var loaded = entityManager.find(com.navio.tripplanningservice.model.Trip.class, trip.getId());
        var loadedStop = entityManager.find(com.navio.tripplanningservice.model.BlockItem.class, stop.getId());
        assertThat(loaded.getInitialSocPct()).isEqualByComparingTo("72.25");
        assertThat(loaded.getEnergyVehicleSnapshot()).isEqualTo(trip.getEnergyVehicleSnapshot());
        assertThat(loaded.getGarageVehicleIds()).isEqualTo(trip.getGarageVehicleIds());
        loaded.setGarageVehicleIds(java.util.List.of());
        assertThat(loadedStop.getObservedSocPct()).isEqualByComparingTo("0");
        loaded.setInitialSocPct(null); loaded.setEnergyVehicleSnapshot(null); loadedStop.setObservedSocPct(null);
        entityManager.flush(); entityManager.clear();
        assertThat(entityManager.find(com.navio.tripplanningservice.model.Trip.class, trip.getId()).getInitialSocPct()).isNull();
        assertThat(entityManager.find(com.navio.tripplanningservice.model.Trip.class, trip.getId()).getGarageVehicleIds()).isEmpty();
        assertThat(entityManager.find(com.navio.tripplanningservice.model.BlockItem.class, stop.getId()).getObservedSocPct()).isNull();
    }
}
