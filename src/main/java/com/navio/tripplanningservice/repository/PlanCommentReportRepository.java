package com.navio.tripplanningservice.repository;

import com.navio.tripplanningservice.dto.publication.CommentReportModeration.ReportedComment;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Reported Shared Plan comments across every plan, for platform moderators.
 *
 * <p>A comment is in the queue while it is visible and has at least one report.
 * Removing the comment takes it out (its reports stay as history); dismissing
 * deletes its reports, so a later report brings it back.
 */
@Repository
@RequiredArgsConstructor
public class PlanCommentReportRepository {

    private final JdbcClient db;

    public long countOpen() {
        return db.sql("""
                SELECT count(DISTINCT c.id) FROM trip.plan_comment c
                JOIN trip.plan_comment_report r ON r.comment_id = c.id
                WHERE c.deleted = false
                """).query(Long.class).single();
    }

    /** Oldest first, so nothing waits behind a stream of newer reports. */
    public List<ReportedComment> open(int limit, long offset) {
        return db.sql("""
                SELECT c.id, c.publication_id, c.user_id, c.author_name, c.content,
                       CASE WHEN p.status = 'ACTIVE' THEN p.token END AS token,
                       p.snapshot->>'title' AS title,
                       count(*) AS report_count,
                       array_agg(DISTINCT r.reason ORDER BY r.reason) AS reasons,
                       min(r.created_at) AS first_reported_at,
                       max(r.created_at) AS last_reported_at
                FROM trip.plan_comment c
                JOIN trip.plan_comment_report r ON r.comment_id = c.id
                JOIN trip.trip_publication p ON p.id = c.publication_id
                WHERE c.deleted = false
                GROUP BY c.id, p.id
                ORDER BY min(r.created_at), c.id
                LIMIT :limit OFFSET :offset
                """).param("limit", limit).param("offset", offset).query((row, index) -> map(row)).list();
    }

    public record Target(UUID commentId, UUID publicationId) {}

    /** The comment when it is still visible and has at least one report. */
    public Optional<Target> openTarget(UUID commentId) {
        return db.sql("""
                SELECT c.id, c.publication_id FROM trip.plan_comment c
                WHERE c.id = :id AND c.deleted = false
                  AND EXISTS (SELECT 1 FROM trip.plan_comment_report r WHERE r.comment_id = c.id)
                FOR UPDATE OF c
                """).param("id", commentId)
                .query((row, index) -> new Target(row.getObject(1, UUID.class), row.getObject(2, UUID.class))).optional();
    }

    public void dismiss(UUID commentId) {
        db.sql("DELETE FROM trip.plan_comment_report WHERE comment_id = :id").param("id", commentId).update();
    }

    private static ReportedComment map(ResultSet row) throws SQLException {
        return new ReportedComment(row.getObject("id", UUID.class), row.getString("token"), row.getString("title"),
                row.getString("content"), row.getObject("user_id", UUID.class), row.getString("author_name"),
                row.getLong("report_count"), reasons(row.getArray("reasons")),
                row.getTimestamp("first_reported_at").toInstant(), row.getTimestamp("last_reported_at").toInstant());
    }

    private static List<String> reasons(Array array) throws SQLException {
        if (array == null) return List.of();
        return Arrays.stream((Object[]) array.getArray()).map(String::valueOf).toList();
    }
}
