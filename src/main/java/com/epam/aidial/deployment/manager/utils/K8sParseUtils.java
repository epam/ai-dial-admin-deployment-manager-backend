package com.epam.aidial.deployment.manager.utils;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.time.Instant;

@Slf4j
@UtilityClass
public class K8sParseUtils {

    public static Instant parseInstant(String timestamp) {
        if (StringUtils.isEmpty(timestamp)) {
            return null;
        }
        try {
            return Instant.parse(timestamp);
        } catch (Exception e) {
            log.debug("Failed to parse timestamp '{}' to Instant", timestamp, e);
            return null;
        }
    }

}
