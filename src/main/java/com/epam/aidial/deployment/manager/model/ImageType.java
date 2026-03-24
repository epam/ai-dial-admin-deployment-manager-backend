package com.epam.aidial.deployment.manager.model;

public enum ImageType {
    MCP,
    ADAPTER,
    INTERCEPTOR,
    APPLICATION;

    public static ImageType of(ImageDefinition def) {
        return switch (def) {
            case McpImageDefinition ignored -> MCP;
            case AdapterImageDefinition ignored -> ADAPTER;
            case InterceptorImageDefinition ignored -> INTERCEPTOR;
            case ApplicationImageDefinition ignored -> APPLICATION;
            default -> throw new IllegalArgumentException("Unsupported image definition type: " + def.getClass().getName());
        };
    }
}
