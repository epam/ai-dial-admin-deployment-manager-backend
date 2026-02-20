package com.epam.aidial.deployment.manager.registry.mcp.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Transport type for a package: stdio, streamable-http, or sse (OpenAPI LocalTransport).
 */
public enum LocalTransportType {

    STDIO("stdio"),
    STREAMABLE_HTTP("streamable-http"),
    SSE("sse");

    private final String value;

    LocalTransportType(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    @JsonCreator
    public static LocalTransportType fromString(String value) {
        if (value == null) {
            return null;
        }
        for (LocalTransportType t : values()) {
            if (t.value.equals(value)) {
                return t;
            }
        }
        return null;
    }
}
