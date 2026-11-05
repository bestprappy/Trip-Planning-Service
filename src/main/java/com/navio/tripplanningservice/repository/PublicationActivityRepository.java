package com.navio.tripplanningservice.repository;

import java.time.LocalDate;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository @RequiredArgsConstructor
public class PublicationActivityRepository {
    private final JdbcClient jdbc;
    public void view(UUID publication){
        jdbc.sql("""
          INSERT INTO trip.publication_daily_activity(publication_id,activity_day,views)
          VALUES (:id,(now() AT TIME ZONE 'UTC')::date,1)
          ON CONFLICT(publication_id,activity_day) DO UPDATE SET views=publication_daily_activity.views+1
          """).param("id",publication).update();
    }
    public Map<UUID,Long> scores(LocalDate today){
        var result=new HashMap<UUID,Long>();
        jdbc.sql("""
          SELECT publication_id,sum(views::numeric+3*copies::numeric) score
          FROM trip.publication_daily_activity WHERE activity_day BETWEEN :first AND :today GROUP BY publication_id
          """).param("first",today.minusDays(6)).param("today",today).query((rs,i)->{
              result.put(rs.getObject("publication_id",UUID.class),rs.getBigDecimal("score").min(java.math.BigDecimal.valueOf(Long.MAX_VALUE)).longValue());return 0;
          }).list();
        return Map.copyOf(result);
    }
}
