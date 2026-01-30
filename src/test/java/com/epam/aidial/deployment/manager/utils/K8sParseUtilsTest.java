package com.epam.aidial.deployment.manager.utils;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import java.time.Instant;

class K8sParseUtilsTest {

    @Test
    void testParseInstant_Null_Null() {
        // When
        var result = K8sParseUtils.parseInstant(null);
        // Then
        Assertions.assertThat(result).isNull();
    }

    @Test
    void testParseInstant_EmptyString_Null() {
        // When
        var result = K8sParseUtils.parseInstant("");
        // Then
        Assertions.assertThat(result).isNull();
    }

    @Test
    void testParseInstant_BlankString_Null() {
        // When
        var result = K8sParseUtils.parseInstant("   ");
        // Then
        Assertions.assertThat(result).isNull();
    }

    @Test
    void testParseInstant_ValidIso8601Timestamp_Instant() {
        // Given
        var timestamp = "2024-01-15T10:30:00Z";
        // When
        var result = K8sParseUtils.parseInstant(timestamp);
        // Then
        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result).isEqualTo(Instant.parse(timestamp));
    }

    @Test
    void testParseInstant_InvalidTimestamp_Null() {
        // Given
        var timestamp = "not-a-valid-timestamp";
        // When
        var result = K8sParseUtils.parseInstant(timestamp);
        // Then
        Assertions.assertThat(result).isNull();
    }

}
