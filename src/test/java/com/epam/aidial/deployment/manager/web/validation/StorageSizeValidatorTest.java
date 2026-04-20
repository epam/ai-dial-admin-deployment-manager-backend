package com.epam.aidial.deployment.manager.web.validation;

import jakarta.validation.ConstraintValidatorContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
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
        validator = new StorageSizeValidator();
        setField(validator, "maxStorageSize", null);
        context = mock(ConstraintValidatorContext.class);
        var builder = mock(ConstraintValidatorContext.ConstraintViolationBuilder.class);
        when(context.buildConstraintViolationWithTemplate(anyString())).thenReturn(builder);
        when(builder.addConstraintViolation()).thenReturn(context);
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
        setField(validator, "maxStorageSize", "100Gi");

        assertThat(validator.isValid("200Gi", context)).isFalse();
        verify(context).disableDefaultConstraintViolation();
        verify(context).buildConstraintViolationWithTemplate(contains("must not exceed"));
    }

    @Test
    void testExceedsMaxStorageSize_plainBytesInput() {
        setField(validator, "maxStorageSize", "100Gi");

        // 200Gi in bytes
        assertThat(validator.isValid("214748364800", context)).isFalse();
        verify(context).disableDefaultConstraintViolation();
        verify(context).buildConstraintViolationWithTemplate(contains("must not exceed"));
    }

    @Test
    void testWithinMaxStorageSize() {
        setField(validator, "maxStorageSize", "100Gi");

        assertThat(validator.isValid("50Gi", context)).isTrue();
    }

    @Test
    void testExactlyAtMaxStorageSize() {
        setField(validator, "maxStorageSize", "100Gi");

        assertThat(validator.isValid("100Gi", context)).isTrue();
    }

    @Test
    void testMaxStorageSizeWithDifferentUnits() {
        setField(validator, "maxStorageSize", "1Ti");

        assertThat(validator.isValid("1024Gi", context)).isTrue();
        assertThat(validator.isValid("1025Gi", context)).isFalse();
    }

    @Test
    void testMaxStorageSizeInPlainBytes() {
        setField(validator, "maxStorageSize", "107374182400"); // 100Gi in bytes

        assertThat(validator.isValid("100Gi", context)).isTrue();
        assertThat(validator.isValid("101Gi", context)).isFalse();
    }

    @Test
    void testNoMaxStorageSize() {
        setField(validator, "maxStorageSize", null);

        assertThat(validator.isValid("999Ti", context)).isTrue();
    }

    @Test
    void testInvalidMaxStorageSizeConfig_skipsUpperBoundCheck() {
        setField(validator, "maxStorageSize", "invalid-config");

        assertThat(validator.isValid("999Ti", context)).isTrue();
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
