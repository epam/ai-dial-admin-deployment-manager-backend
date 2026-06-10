package com.epam.aidial.deployment.manager.configuration;

import io.fabric8.kubernetes.api.model.IntOrString;
import io.fabric8.kubernetes.api.model.Quantity;
import tools.jackson.core.JsonGenerator;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.ValueSerializer;
import tools.jackson.databind.module.SimpleModule;

/**
 * Jackson 3 (de)serializers for the Fabric8 Kubernetes model types that carry custom Jackson 2
 * (de)serializers in their class annotations. Jackson 3 ignores those Jackson 2 annotations, so
 * without this module {@code Quantity} would serialize as {@code {"amount":"1000","format":"m"}}
 * instead of {@code "1000m"} and {@code IntOrString} could not be deserialized at all.
 */
public class KubernetesModelJacksonModule extends SimpleModule {

    public KubernetesModelJacksonModule() {
        super("kubernetes-model");
        addSerializer(Quantity.class, new QuantitySerializer());
        addDeserializer(Quantity.class, new QuantityDeserializer());
        addSerializer(IntOrString.class, new IntOrStringSerializer());
        addDeserializer(IntOrString.class, new IntOrStringDeserializer());
    }

    private static final class QuantitySerializer extends ValueSerializer<Quantity> {
        @Override
        public void serialize(Quantity value, JsonGenerator generator, SerializationContext context) {
            var amountWithFormat = new StringBuilder();
            if (value.getAmount() != null) {
                amountWithFormat.append(value.getAmount());
            }
            if (value.getFormat() != null) {
                amountWithFormat.append(value.getFormat());
            }
            generator.writeString(amountWithFormat.toString());
        }
    }

    private static final class QuantityDeserializer extends ValueDeserializer<Quantity> {
        @Override
        public Quantity deserialize(JsonParser parser, DeserializationContext context) {
            return new Quantity(parser.getValueAsString());
        }
    }

    private static final class IntOrStringSerializer extends ValueSerializer<IntOrString> {
        @Override
        public void serialize(IntOrString value, JsonGenerator generator, SerializationContext context) {
            if (value.getIntVal() != null) {
                generator.writeNumber(value.getIntVal());
            } else {
                generator.writeString(value.getStrVal());
            }
        }
    }

    private static final class IntOrStringDeserializer extends ValueDeserializer<IntOrString> {
        @Override
        public IntOrString deserialize(JsonParser parser, DeserializationContext context) {
            if (parser.currentToken() == JsonToken.VALUE_NUMBER_INT) {
                return new IntOrString(parser.getIntValue());
            }
            return new IntOrString(parser.getValueAsString());
        }
    }
}
