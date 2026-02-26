package com.epam.aidial.deployment.manager.web.validation;

import jakarta.validation.ConstraintValidatorContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullSource;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.mockito.Mockito.mock;

class SemanticVersionValidatorTest {

    private SemanticVersionValidator validator;
    private ConstraintValidatorContext context;

    @BeforeEach
    void setUp() {
        validator = new SemanticVersionValidator();
        context = mock(ConstraintValidatorContext.class);
    }

    @ParameterizedTest
    @NullSource
    void isValid_shouldReturnTrue_whenValueIsNull(String value) {
        assertThat(validator.isValid(value, context)).isTrue();
    }

    @ParameterizedTest
    @MethodSource("validVersionsProvider")
    void isValid_shouldReturnTrue_whenVersionIsValid(String version) {
        assertThat(validator.isValid(version, context)).isTrue();
    }

    @ParameterizedTest
    @MethodSource("invalidVersionsProvider")
    void isValid_shouldReturnFalse_whenVersionIsInvalid(String version, String reason) {
        assertThat(validator.isValid(version, context))
                .as("Should be invalid: %s - %s", version, reason)
                .isFalse();
    }

    static Stream<Arguments> validVersionsProvider() {
        return Stream.of(
                arguments("0.0.0"),
                arguments("1.0.0"),
                arguments("1.2.3"),
                arguments("12.34.56"),
                arguments("0.1.0")
        );
    }

    static Stream<Arguments> invalidVersionsProvider() {
        return Stream.of(
                arguments("", "Empty string"),
                arguments("  ", "Whitespace only"),
                arguments("1.0", "Only two segments"),
                arguments("1.0.0.0", "Four segments"),
                arguments("v1.0.0", "Prefix v"),
                arguments("1.0.0-SNAPSHOT", "Suffix not allowed"),
                arguments("1.0.0-beta", "Suffix not allowed"),
                arguments("a.b.c", "Non-numeric"),
                arguments("1.a.0", "Non-numeric middle"),
                arguments("1.0.b", "Non-numeric patch"),
                arguments("-1.0.0", "Negative major"),
                arguments("1 .0.0", "Space in version")
        );
    }
}
