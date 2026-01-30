package com.epam.aidial.deployment.manager.web.validation;

import jakarta.validation.ConstraintValidatorContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullSource;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.mockito.Mockito.mock;

class ModelNameValidatorTest {

    private HuggingFaceModelNameValidator validator;
    private ConstraintValidatorContext context;

    @BeforeEach
    void setUp() {
        validator = new HuggingFaceModelNameValidator();
        context = mock(ConstraintValidatorContext.class);
    }

    @ParameterizedTest
    @NullSource
    void isValid_shouldReturnTrue_whenValueIsNull(String value) {
        assertThat(validator.isValid(value, context)).isTrue();
    }

    @ParameterizedTest
    @MethodSource("validModelNamesProvider")
    void isValid_shouldReturnTrue_whenModelNameIsValid(String modelName) {
        assertThat(validator.isValid(modelName, context)).isTrue();
    }

    @ParameterizedTest
    @MethodSource("invalidModelNamesProvider")
    void isValid_shouldReturnFalse_whenModelNameIsInvalid(String modelName, String reason) {
        assertThat(validator.isValid(modelName, context))
                .as("Should be invalid: %s - %s", modelName, reason)
                .isFalse();
    }

    static Stream<Arguments> validModelNamesProvider() {
        return Stream.of(
                // Basic valid cases
                arguments("user/model"),
                arguments("user-name/model-name"),
                arguments("user123/model123"),
                arguments("User123/Model123"),

                // Valid user_name cases
                arguments("a/b"), // Minimal valid
                arguments("user-name/model"), // User with dash
                arguments("user123/model"), // User with numbers
                arguments("UserName/model"), // User with mixed case
                arguments("a".repeat(42) + "/model"), // Max user name length (42)

                // Valid model_name cases
                arguments("user/model-name"), // Model with dash
                arguments("user/model_name"), // Model with underscore
                arguments("user/model.name"), // Model with dot
                arguments("user/model-name_name.model"), // Model with all allowed chars
                arguments("user/model" + "x".repeat(96 - "model".length())), // Max model name length (96)
                arguments("user/model.git123"), // Ends with .git but not exactly .git
                arguments("user/model.ipynb123"), // Ends with .ipynb but not exactly .ipynb
                arguments("user/model.git.backup"), // Contains .git but doesn't end with it
                arguments("user/model.ipynb.backup"), // Contains .ipynb but doesn't end with it

                // Edge cases
                arguments("user123/model456"),
                arguments("test-user/test-model"),
                arguments("TestUser/TestModel"),
                arguments("user123/model_name.test")
        );
    }

    static Stream<Arguments> invalidModelNamesProvider() {
        return Stream.of(
                // Missing slash
                arguments("usermodel", "Missing '/' separator"),
                arguments("user", "No slash"),
                arguments("model", "No slash"),

                // Multiple slashes
                arguments("user/model/submodel", "Multiple slashes"),
                arguments("user/model/extra", "Multiple slashes"),

                // Empty parts
                arguments("/model", "Empty user_name"),
                arguments("user/", "Empty model_name"),
                arguments("/", "Both parts empty"),

                // Invalid user_name - starts with dash
                arguments("-user/model", "User name starts with '-'"),
                arguments("-user-name/model", "User name starts with '-'"),

                // Invalid user_name - ends with dash
                arguments("user-/model", "User name ends with '-'"),
                arguments("user-name-/model", "User name ends with '-'"),

                // Invalid user_name - double dash
                arguments("user--name/model", "User name contains '--'"),
                arguments("user--/model", "User name contains '--'"),

                // Invalid user_name - invalid characters
                arguments("user_name/model", "User name contains '_'"),
                arguments("user.name/model", "User name contains '.'"),
                arguments("user name/model", "User name contains space"),
                arguments("user@name/model", "User name contains '@'"),
                arguments("user#name/model", "User name contains '#'"),

                // Invalid user_name - too long
                arguments("a".repeat(43) + "/model", "User name exceeds max length (43 > 42)"),

                // Invalid model_name - starts with dash
                arguments("user/-model", "Model name starts with '-'"),
                arguments("user/-model-name", "Model name starts with '-'"),

                // Invalid model_name - ends with dash
                arguments("user/model-", "Model name ends with '-'"),
                arguments("user/model-name-", "Model name ends with '-'"),

                // Invalid model_name - starts with dot
                arguments("user/.model", "Model name starts with '.'"),
                arguments("user/.model-name", "Model name starts with '.'"),

                // Invalid model_name - ends with dot
                arguments("user/model.", "Model name ends with '.'"),
                arguments("user/model-name.", "Model name ends with '.'"),

                // Invalid model_name - double dash
                arguments("user/model--name", "Model name contains '--'"),
                arguments("user/model--", "Model name contains '--'"),

                // Invalid model_name - double dot
                arguments("user/model..name", "Model name contains '..'"),
                arguments("user/model..", "Model name contains '..'"),

                // Invalid model_name - ends with .git
                arguments("user/model.git", "Model name ends with '.git'"),
                arguments("user/test.git", "Model name ends with '.git'"),

                // Invalid model_name - ends with .ipynb
                arguments("user/model.ipynb", "Model name ends with '.ipynb'"),
                arguments("user/test.ipynb", "Model name ends with '.ipynb'"),

                // Invalid model_name - invalid characters
                arguments("user/model name", "Model name contains space"),
                arguments("user/model@name", "Model name contains '@'"),
                arguments("user/model#name", "Model name contains '#'"),
                arguments("user/model/name", "Model name contains '/'"),
                arguments("user/model:name", "Model name contains ':'"),

                // Invalid model_name - too long
                arguments("user/" + "x".repeat(97), "Model name exceeds max length (97 > 96)"),

                // Special cases
                arguments("", "Empty string"),
                arguments("  ", "Whitespace only")
        );
    }

    @Test
    void isValid_shouldReturnFalse_whenUserAndModelAreBothEmpty() {
        assertThat(validator.isValid("", context)).isFalse();
    }

    @Test
    void isValid_shouldReturnFalse_whenOnlyWhitespace() {
        assertThat(validator.isValid("   ", context)).isFalse();
        assertThat(validator.isValid("\t", context)).isFalse();
        assertThat(validator.isValid("\n", context)).isFalse();
    }

    @Test
    void isValid_shouldReturnTrue_whenMaxLengthBoundary() {
        // User name at max length (42)
        String maxUserName = "a".repeat(42);
        assertThat(validator.isValid(maxUserName + "/model", context)).isTrue();

        // Model name at max length (96)
        String maxModelName = "x".repeat(96);
        assertThat(validator.isValid("user/" + maxModelName, context)).isTrue();

        // Both at max length
        assertThat(validator.isValid(maxUserName + "/" + maxModelName, context)).isTrue();
    }

    @Test
    void isValid_shouldReturnFalse_whenExceedsMaxLength() {
        // User name exceeds max length (43)
        String tooLongUserName = "a".repeat(43);
        assertThat(validator.isValid(tooLongUserName + "/model", context)).isFalse();

        // Model name exceeds max length (97)
        String tooLongModelName = "x".repeat(97);
        assertThat(validator.isValid("user/" + tooLongModelName, context)).isFalse();
    }
}
