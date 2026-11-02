package com.navio.tripplanningservice.repository;
import com.navio.tripplanningservice.model.TripPublication;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import java.util.UUID;
import java.util.stream.Stream;
/** Read-only catalogue query; never loads private Trip state. */
public interface ExploreSearchRepository extends Repository<TripPublication, UUID> {
 @Query("""
 select p from TripPublication p where p.listedInExplore = true
 and p.status = com.navio.tripplanningservice.model.TripPublication.PublicationStatus.ACTIVE
 and p.token is not null and p.sanitizerVersion >= :version order by p.id
 """)
 @org.springframework.data.jpa.repository.QueryHints(@jakarta.persistence.QueryHint(name="org.hibernate.fetchSize", value="64"))
 Stream<TripPublication> streamListed(@Param("version") int version);
}
