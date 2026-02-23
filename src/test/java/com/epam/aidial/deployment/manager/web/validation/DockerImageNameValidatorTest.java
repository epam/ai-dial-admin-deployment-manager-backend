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

class DockerImageNameValidatorTest {

    private DockerImageNameValidator validator;
    private ConstraintValidatorContext context;

    @BeforeEach
    void setUp() {
        validator = new DockerImageNameValidator();
        context = mock(ConstraintValidatorContext.class);
    }

    @ParameterizedTest
    @NullSource
    void isValid_shouldReturnTrue_whenValueIsNull(String value) {
        assertThat(validator.isValid(value, context)).isTrue();
    }

    @ParameterizedTest
    @MethodSource("validImageRefsProvider")
    void isValid_shouldReturnTrue_whenImageRefIsValid(String imageRef) {
        assertThat(validator.isValid(imageRef, context)).isTrue();
    }

    @ParameterizedTest
    @MethodSource("invalidImageRefsProvider")
    void isValid_shouldReturnFalse_whenImageRefIsInvalid(String imageRef, String reason) {
        assertThat(validator.isValid(imageRef, context))
                .as("Should be invalid: %s - %s", imageRef, reason)
                .isFalse();
    }

    static Stream<Arguments> validImageRefsProvider() {
        return Stream.of(
                arguments("nginx"),
                arguments("nginx:latest"),
                arguments("nginx:1.25"),
                arguments("myreg.io/image:tag"),
                arguments("ghcr.io/org/repo:v1.0.0"),
                arguments("docker.io/library/alpine:3.19"),
                arguments("localhost:5000/myimage:tag")
        );
    }

    static Stream<Arguments> invalidImageRefsProvider() {
        return Stream.of(
                arguments("", "Empty string"),
                arguments("  ", "Whitespace only"),
                arguments("invalid:tag:extra", "Too many colons in tag part"),
                arguments("@invalid", "Invalid leading character"),
                arguments("-leading-dash/image", "Invalid path segment")
        );
    }
}
