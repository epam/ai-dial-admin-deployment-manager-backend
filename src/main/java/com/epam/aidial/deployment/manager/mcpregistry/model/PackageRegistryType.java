package com.epam.aidial.deployment.manager.mcpregistry.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Registry type for a package: npm, pypi, oci, nuget, mcpb (OpenAPI Package schema).
 */
public enum PackageRegistryType {

    NPM("npm"),
    PYPI("pypi"),
    OCI("oci"),
    NUGET("nuget"),
    MCPB("mcpb");

    private final String value;

    PackageRegistryType(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    @JsonCreator
    public static PackageRegistryType fromString(String value) {
        if (value == null) {
            return null;
        }
        for (PackageRegistryType t : values()) {
            if (t.value.equals(value)) {
                return t;
            }
        }
        return null;
    }
}
