package com.navio.tripplanningservice.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.NullNode;
import org.springframework.stereotype.Component;
import tools.jackson.core.JsonGenerator;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.ValueSerializer;
import tools.jackson.databind.exc.MismatchedInputException;
import tools.jackson.databind.module.SimpleModule;

/**
 * Lets the HTTP layer, which runs Jackson 3, read and write the Jackson 2 {@link JsonNode}
 * fields of the planner DTOs ({@code energyState}, {@code observedSocCheckpoint},
 * {@code arrivalMode}).
 *
 * <p>Without it Jackson 3 treats a Jackson 2 node as a plain bean and writes its getters
 * ({@code {"array":false,...}}), which the client rejects, so no saved trip loads. Reading
 * keeps the planner's three-way contract: an omitted property stays {@code null} (keep the
 * saved value) while an explicit JSON {@code null} becomes {@link NullNode} (clear it).
 */
@Component
public class LegacyJsonNodeModule extends SimpleModule {

    private static final ObjectMapper LEGACY_MAPPER = new ObjectMapper();

    public LegacyJsonNodeModule() {
        super("legacy-json-node");
        addSerializer(JsonNode.class, new LegacyJsonNodeSerializer());
        addDeserializer(JsonNode.class, new LegacyJsonNodeDeserializer());
    }

    private static final class LegacyJsonNodeSerializer extends ValueSerializer<JsonNode> {
        @Override
        public void serialize(JsonNode value, JsonGenerator generator, SerializationContext context) {
            // A Jackson 2 node prints itself as valid JSON.
            generator.writeRawValue(value.toString());
        }
    }

    private static final class LegacyJsonNodeDeserializer extends ValueDeserializer<JsonNode> {
        @Override
        public JsonNode deserialize(JsonParser parser, DeserializationContext context) {
            String json = context.readTree(parser).toString();
            try {
                return LEGACY_MAPPER.readTree(json);
            } catch (JsonProcessingException exception) {
                throw MismatchedInputException.from(parser, JsonNode.class, "Invalid JSON value");
            }
        }

        /** An explicit JSON {@code null} clears the stored value. */
        @Override
        public JsonNode getNullValue(DeserializationContext context) {
            return NullNode.getInstance();
        }

        /** An omitted property keeps the stored value; Jackson 3 would otherwise reuse the null value. */
        @Override
        public Object getAbsentValue(DeserializationContext context) {
            return null;
        }
    }
}
