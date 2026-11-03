package com.navio.tripplanningservice.dto.publication;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import java.io.IOException;
import java.time.MonthDay;
import java.time.LocalDate;

/** Accept old ISO dates on reads, but retain no year in the public model. */
public final class PublicMonthDayDeserializer extends JsonDeserializer<MonthDay> {
    @Override public MonthDay deserialize(JsonParser parser, DeserializationContext context) throws IOException {
        var node = parser.getCodec().readTree(parser);
        String value = node.toString().replace("\"", "");
        try {
            if (value.matches("\\d{4}-\\d{2}-\\d{2}")) return MonthDay.from(LocalDate.parse(value));
            return MonthDay.parse(value.startsWith("--") ? value : "--" + value);
        } catch (RuntimeException invalid) {
            throw context.weirdStringException(value, MonthDay.class, "Expected month and day (MM-dd)");
        }
    }
}
