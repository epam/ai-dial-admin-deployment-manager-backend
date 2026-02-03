package com.epam.aidial.deployment.manager.validation;

import com.epam.aidial.deployment.manager.web.dto.deployment.CreateDeploymentRequestDto;
import com.epam.aidial.deployment.manager.web.validation.ScaleConfigurationValidator;
import jakarta.validation.ConstraintValidatorContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ScaleConfigurationValidatorTest {

    private ScaleConfigurationValidator validator;
    private ConstraintValidatorContext context;

    @BeforeEach
    void setUp() {
        validator = new ScaleConfigurationValidator();
        context = mock(ConstraintValidatorContext.class);
        ConstraintValidatorContext.ConstraintViolationBuilder builder =
                mock(ConstraintValidatorContext.ConstraintViolationBuilder.class);
        when(context.buildConstraintViolationWithTemplate(anyString())).thenReturn(builder);
        when(builder.addConstraintViolation()).thenReturn(context);
    }

    @Test
    void shouldBeValidWhenValueIsNull() {
        assertTrue(validator.isValid(null, context));
        verifyNoInteractions(context);
    }

    @Test
    void shouldBeValidWhenAllScalesAreNull() {
        var dto = new TestCreateDeploymentRequestDto(null, null, null);

        assertTrue(validator.isValid(dto, context));
        verifyNoInteractions(context);
    }

    @Test
    void shouldBeValidForValuesWithinRangeAndCorrectOrder() {
        var dto = new TestCreateDeploymentRequestDto(0, 5, 10);

        assertTrue(validator.isValid(dto, context));
    }

    @Test
    void shouldFailWhenMinScaleBelowRange() {
        var dto = new TestCreateDeploymentRequestDto(-1, 5, 10);

        assertFalse(validator.isValid(dto, context));
        verify(context).buildConstraintViolationWithTemplate(
                "minScale must be between 0 and 10");
    }

    @Test
    void shouldFailWhenInitialScaleAboveRange() {
        var dto = new TestCreateDeploymentRequestDto(0, 11, 10);

        assertFalse(validator.isValid(dto, context));
        verify(context).buildConstraintViolationWithTemplate(
                "initialScale must be between 0 and 10");
    }

    @Test
    void shouldFailWhenMaxScaleAboveRange() {
        var dto = new TestCreateDeploymentRequestDto(0, 5, 11);

        assertFalse(validator.isValid(dto, context));
        verify(context).buildConstraintViolationWithTemplate(
                "maxScale must be between 0 and 10");
    }

    @Test
    void shouldFailWhenMinScaleGreaterThanInitialScale() {
        var dto = new TestCreateDeploymentRequestDto(6, 5, 10);

        assertFalse(validator.isValid(dto, context));
        verify(context).buildConstraintViolationWithTemplate(
                "minScale must be less than or equal to initialScale");
    }

    @Test
    void shouldFailWhenInitialScaleGreaterThanMaxScale() {
        var dto = new TestCreateDeploymentRequestDto(0, 6, 5);

        assertFalse(validator.isValid(dto, context));
        verify(context).buildConstraintViolationWithTemplate(
                "initialScale must be less than or equal to maxScale");
    }

    @Test
    void shouldFailWhenMinScaleGreaterThanMaxScale() {
        var dto = new TestCreateDeploymentRequestDto(6, 7, 5);

        assertFalse(validator.isValid(dto, context));
        verify(context).buildConstraintViolationWithTemplate(
                "minScale must be less than or equal to maxScale");
    }

    private static class TestCreateDeploymentRequestDto extends CreateDeploymentRequestDto {
        TestCreateDeploymentRequestDto(Integer minScale, Integer initialScale, Integer maxScale) {
            setMinScale(minScale);
            setInitialScale(initialScale);
            setMaxScale(maxScale);
        }
    }
}

