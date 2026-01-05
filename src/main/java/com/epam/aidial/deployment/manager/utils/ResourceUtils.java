package com.epam.aidial.deployment.manager.utils;

import lombok.experimental.UtilityClass;
import org.apache.commons.io.IOUtils;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

@UtilityClass
public final class ResourceUtils {

    public static String readResource(String resource) {
        try (InputStream resourceAsStream = ResourceUtils.class.getResourceAsStream(resource)) {
            return IOUtils.toString(resourceAsStream, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalArgumentException("Can't get resource: %s".formatted(resource), e);
        }
    }

}