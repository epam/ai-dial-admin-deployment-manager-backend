package com.epam.aidial.deployment.manager.model;

public enum EventType {
    NORMAL,
    WARNING;

    public static EventType fromString(String value) {
        if (value == null) {
            return null;
        }
        for (EventType type : EventType.values()) {
            if (type.name().equalsIgnoreCase(value)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown event type: " + value);
    }
}
