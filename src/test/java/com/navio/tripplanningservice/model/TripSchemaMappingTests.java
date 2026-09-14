package com.navio.tripplanningservice.model;

import jakarta.persistence.Column;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TripSchemaMappingTests {

    // Production runs ddl-auto=validate. Without the CHAR JDBC type Hibernate
    // expects varchar and refuses to start against the bpchar column from V10.
    @Test
    void destinationCountryCodeUsesTheFixedWidthJdbcTypeCreatedByFlyway() throws NoSuchFieldException {
        var countryCode = Trip.class.getDeclaredField("destinationCountryCode");

        assertThat(countryCode.getAnnotation(JdbcTypeCode.class).value()).isEqualTo(SqlTypes.CHAR);
        assertThat(countryCode.getAnnotation(Column.class).columnDefinition()).isEqualTo("char(2)");
    }
}
