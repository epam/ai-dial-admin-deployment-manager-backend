package com.epam.aidial.deployment.manager.web.validation;

import jakarta.validation.ConstraintValidatorContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NoPathTraversalValidatorTest {

    private NoPathTraversalValidator validator;
    private ConstraintValidatorContext context;
    private ConstraintValidatorContext.ConstraintViolationBuilder builder;

    @BeforeEach
    void setUp() {
        validator = new NoPathTraversalValidator();
        context = mock(ConstraintValidatorContext.class);
        builder = mock(ConstraintValidatorContext.ConstraintViolationBuilder.class);

        // Mock the fluent API chain for building violations
        when(context.buildConstraintViolationWithTemplate(anyString())).thenReturn(builder);
        when(builder.addConstraintViolation()).thenReturn(context);
    }

    @ParameterizedTest
    @NullSource // 1. Null is valid (handled by @NotNull usually)
    @ValueSource(strings = {
            "simple",
            "path/to/file",
            "file.txt",
            "data_v1.2",
            "my-folder/sub-folder",
            "valid..filename.txt" // Double dots inside filename are technically valid
    })
    void shouldReturnTrue_WhenPathIsValid(String path) {
        boolean isValid = validator.isValid(path, context);
        assertThat(isValid).as("Expected path to be valid: " + path).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"~", "~/docs", "docs/~", "/home/user/~"})
    void shouldFail_WhenContainsTilde(String path) {
        assertInvalid(path, "Path must not contain the tilde character ('~')");
    }

    @ParameterizedTest
    @ValueSource(strings = {"/usr", "bin/", "/etc/", "/opt/data/"})
    void shouldFail_WhenStartsOrEndsWithSlash(String path) {
        assertInvalid(path, "Path must not start or end with '/'");
    }

    @ParameterizedTest
    @ValueSource(strings = {".", "./config", "config/./data", "data/."})
    void shouldFail_WhenContainsCurrentDirectoryTraversal(String path) {
        assertInvalid(path, "Path must not contain current directory references ('.', './')");
    }

    @ParameterizedTest
    @ValueSource(strings = {"..", "../etc", "opt/../bin", "bin/.."})
    void shouldFail_WhenContainsParentDirectoryTraversal(String path) {
        assertInvalid(path, "Path must not contain parent directory references ('..', '../')");
    }

    /**
     * Helper to assert that the path is invalid AND the correct error message was set.
     */
    private void assertInvalid(String path, String expectedMessage) {
        // 1. Check return value
        boolean isValid = validator.isValid(path, context);
        assertThat(isValid).as("Expected path to be INVALID: " + path).isFalse();

        // 2. Verify default message was disabled
        verify(context).disableDefaultConstraintViolation();

        // 3. Verify specific error message was added
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(context).buildConstraintViolationWithTemplate(captor.capture());

        String actualMessage = captor.getValue();
        if (!actualMessage.contains(expectedMessage)) {
            throw new AssertionError(
                    "Expected error message to contain: [" + expectedMessage + "] but was: [" + actualMessage + "]");
        }
    }

}