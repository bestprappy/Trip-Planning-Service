package com.navio.tripplanningservice.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Private event ledger; only aggregate numbers leave the service. */
@Repository
@RequiredArgsConstructor
public class SharedPlanCopyRepository {
    private final JdbcClient jdbc;
    public record CopyStats(long copies, long travelers) {
        public static final CopyStats ZERO = new CopyStats(0, 0);
    }
    public void recordSuccessfulCopy(UUID publicationId, UUID copiedTripId, UUID userId) {
        int inserted=jdbc.sql("""
            INSERT INTO trip.shared_plan_copy (copied_trip_id, publication_id, copied_by)
            VALUES (:trip, :publication, :user) ON CONFLICT (copied_trip_id) DO NOTHING
            """).param("trip", copiedTripId).param("publication", publicationId).param("user", userId).update();
        if(inserted>0)activityCopy(publicationId);
    }
    private void activityCopy(UUID id){
        jdbc.sql("""
          INSERT INTO trip.publication_daily_activity(publication_id,activity_day,copies)
          VALUES (:id,(now() AT TIME ZONE 'UTC')::date,1)
          ON CONFLICT(publication_id,activity_day) DO UPDATE SET copies=publication_daily_activity.copies+1
          """).param("id",id).update();
    }
    public Map<UUID, CopyStats> counts(Collection<UUID> publicationIds) {
        if (publicationIds.isEmpty()) return Map.of();
        Map<UUID, CopyStats> result = new LinkedHashMap<>();
        jdbc.sql("""
            SELECT publication_id, count(*) AS copies, count(DISTINCT copied_by) AS travelers
            FROM trip.shared_plan_copy WHERE publication_id IN (:ids) GROUP BY publication_id
            """).param("ids", publicationIds).query((row, index) -> {
                result.put(row.getObject("publication_id", UUID.class), new CopyStats(row.getLong("copies"), row.getLong("travelers")));
                return row.getObject("publication_id", UUID.class);
            }).list();
        return result;
    }
}
