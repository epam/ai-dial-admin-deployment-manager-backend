package com.epam.aidial.deployment.manager.web.validation;

import jakarta.validation.ConstraintValidatorContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.util.ReflectionTestUtils.setField;

class StorageSizeValidatorTest {

    private StorageSizeValidator validator;
    private ConstraintValidatorContext context;

    @BeforeEach
    void setUp() {
        validator = newValidator(null);
        context = mock(ConstraintValidatorContext.class);
        var builder = mock(ConstraintValidatorContext.ConstraintViolationBuilder.class);
        when(context.buildConstraintViolationWithTemplate(anyString())).thenReturn(builder);
        when(builder.addConstraintViolation()).thenReturn(context);
    }

    private static StorageSizeValidator newValidator(String maxStorageSize) {
        var v = new StorageSizeValidator();
        setField(v, "maxStorageSize", maxStorageSize);
        v.initialize(null);
        return v;
    }

    @Test
    void testNullValue() {
        assertThat(validator.isValid(null, context)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"20Gi", "500Mi", "1Ti", "100Ki", "1Pi", "1Ei"})
    void testValidBinarySuffixes(String value) {
        assertThat(validator.isValid(value, context)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"21474836480", "1073741824", "1"})
    void testValidPlainBytes(String value) {
        assertThat(validator.isValid(value, context)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"20G", "500M", "1T", "100k"})
    void testValidDecimalSuffixes(String value) {
        assertThat(validator.isValid(value, context)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"abc", "", "Gi", "fooGi", "100K"})
    void testInvalidFormats(String value) {
        assertThat(validator.isValid(value, context)).isFalse();
    }

    @Test
    void testFractionalValueAccepted() {
        assertThat(validator.isValid("0.5Gi", context)).isTrue();
    }

    @Test
    void testNegativeValueRejected() {
        assertThat(validator.isValid("-1Gi", context)).isFalse();
        verify(context).disableDefaultConstraintViolation();
        verify(context).buildConstraintViolationWithTemplate(contains("positive"));
    }

    @Test
    void testNegativePlainBytesRejected() {
        assertThat(validator.isValid("-100", context)).isFalse();
        verify(context).disableDefaultConstraintViolation();
        verify(context).buildConstraintViolationWithTemplate(contains("positive"));
    }

    @Test
    void testZeroValueRejected() {
        assertThat(validator.isValid("0", context)).isFalse();
        verify(context).disableDefaultConstraintViolation();
        verify(context).buildConstraintViolationWithTemplate(contains("positive"));
    }

    @Test
    void testZeroWithSuffixRejected() {
        assertThat(validator.isValid("0Gi", context)).isFalse();
        verify(context).disableDefaultConstraintViolation();
        verify(context).buildConstraintViolationWithTemplate(contains("positive"));
    }

    @Test
    void testExceedsMaxStorageSize() {
        var v = newValidator("100Gi");

        assertThat(v.isValid("200Gi", context)).isFalse();
        verify(context).disableDefaultConstraintViolation();
        verify(context).buildConstraintViolationWithTemplate(contains("must not exceed"));
    }

    @Test
    void testExceedsMaxStorageSize_plainBytesInput() {
        var v = newValidator("100Gi");

        // 200Gi in bytes
        assertThat(v.isValid("214748364800", context)).isFalse();
        verify(context).disableDefaultConstraintViolation();
        verify(context).buildConstraintViolationWithTemplate(contains("must not exceed"));
    }

    @Test
    void testWithinMaxStorageSize() {
        var v = newValidator("100Gi");

        assertThat(v.isValid("50Gi", context)).isTrue();
    }

    @Test
    void testExactlyAtMaxStorageSize() {
        var v = newValidator("100Gi");

        assertThat(v.isValid("100Gi", context)).isTrue();
    }

    @Test
    void testMaxStorageSizeWithDifferentUnits() {
        var v = newValidator("1Ti");

        assertThat(v.isValid("1024Gi", context)).isTrue();
        assertThat(v.isValid("1025Gi", context)).isFalse();
    }

    @Test
    void testMaxStorageSizeInPlainBytes() {
        var v = newValidator("107374182400"); // 100Gi in bytes

        assertThat(v.isValid("100Gi", context)).isTrue();
        assertThat(v.isValid("101Gi", context)).isFalse();
    }

    @Test
    void testNoMaxStorageSize() {
        var v = newValidator(null);

        assertThat(v.isValid("999Ti", context)).isTrue();
    }

    @Test
    void testInvalidMaxStorageSizeConfig_throwsOnInit() {
        var v = new StorageSizeValidator();
        setField(v, "maxStorageSize", "invalid-config");

        assertThatThrownBy(() -> v.initialize(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("app.validation.resources.max-storage-size")
                .hasMessageContaining("invalid-config");
    }

    @Test
    void testSmallestValidValue() {
        assertThat(validator.isValid("1", context)).isTrue();
    }

    @Test
    void testLargeValue_1Ei() {
        assertThat(validator.isValid("1Ei", context)).isTrue();
    }
}
