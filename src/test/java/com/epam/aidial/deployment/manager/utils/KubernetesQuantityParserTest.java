package com.epam.aidial.deployment.manager.utils;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class KubernetesQuantityParserTest {

    @ParameterizedTest
    @CsvSource({
            "1,          1",
            "1024,       1024",
            "1Ki,        1024",
            "1Mi,        1048576",
            "1Gi,        1073741824",
            "20Gi,       21474836480",
            "1Ti,        1099511627776",
            "1Pi,        1125899906842624",
            "100k,       100000",
            "1M,         1000000",
            "1G,         1000000000",
            "1T,         1000000000000"
    })
    void shouldParseValidQuantities(String input, String expectedBytes) {
        BigDecimal result = KubernetesQuantityParser.parseToBytes(input);

        assertThat(result).isEqualByComparingTo(new BigDecimal(expectedBytes));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"abc", "Gi", "fooGi", "100K"})
    void shouldReturnNullForInvalidQuantities(String input) {
        assertThat(KubernetesQuantityParser.parseToBytes(input)).isNull();
    }

    @Test
    void shouldParseNegativeQuantity() {
        BigDecimal result = KubernetesQuantityParser.parseToBytes("-1Gi");

        assertThat(result).isNegative();
    }

    @Test
    void shouldParseZero() {
        BigDecimal result = KubernetesQuantityParser.parseToBytes("0");

        assertThat(result).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void shouldParseFractionalQuantity() {
        BigDecimal result = KubernetesQuantityParser.parseToBytes("0.5Gi");

        assertThat(result).isEqualByComparingTo(new BigDecimal("536870912"));
    }
}
