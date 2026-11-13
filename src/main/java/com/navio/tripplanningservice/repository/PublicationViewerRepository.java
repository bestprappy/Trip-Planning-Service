package com.navio.tripplanningservice.repository;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Remembers which browsers have already opened a listed plan, so each browser
 * adds one view no matter how often it reloads.
 */
@Repository
@RequiredArgsConstructor
public class PublicationViewerRepository {

    private final JdbcClient jdbc;

    /**
     * Records the visitor against the publication.
     *
     * @return {@code true} only the first time this visitor opens this publication
     */
    public boolean registerFirstView(UUID publicationId, UUID visitorId) {
        return jdbc.sql("""
                INSERT INTO trip.publication_viewer(publication_id, visitor_id)
                VALUES (:publication, :visitor)
                ON CONFLICT (publication_id, visitor_id) DO NOTHING
                """)
                .param("publication", publicationId)
                .param("visitor", visitorId)
                .update() == 1;
    }
}
