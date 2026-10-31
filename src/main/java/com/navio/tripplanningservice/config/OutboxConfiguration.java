package com.navio.tripplanningservice.config;

import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.kafka.autoconfigure.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.Clock;

/** Runs the trip.events.v1 outbox relay and its retention sweep on Spring's scheduler. */
@Configuration
@EnableScheduling
public class OutboxConfiguration {

    public static final String TRIP_EVENTS_TEMPLATE = "tripEventsKafkaTemplate";

    @Bean
    @ConditionalOnMissingBean
    Clock clock() {
        return Clock.systemUTC();
    }

    /**
     * The outbox's own producer, with String key and value serializers set on the
     * factory itself. The Spring Cloud Stream Kafka binder (pulled in by Cloud Bus)
     * defaults the shared {@code spring.kafka.producer.*} serializers to byte
     * arrays, which makes Boot's default template reject String records. Changing
     * the shared properties could break the bus, so this producer opts out instead.
     * Connection, acks and timeouts still come from {@code spring.kafka.*}.
     */
    @Bean(TRIP_EVENTS_TEMPLATE)
    KafkaTemplate<String, String> tripEventsKafkaTemplate(KafkaProperties kafka) {
        return new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(
                kafka.buildProducerProperties(), new StringSerializer(), new StringSerializer()));
    }
}
