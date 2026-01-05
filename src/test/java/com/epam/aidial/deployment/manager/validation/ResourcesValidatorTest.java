package com.epam.aidial.deployment.manager.validation;

import com.epam.aidial.deployment.manager.web.dto.ResourcesDto;
import com.epam.aidial.deployment.manager.web.validation.ResourcesValidator;
import jakarta.validation.ConstraintValidatorContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ResourcesValidatorTest {

    private ResourcesValidator validator;
    private ConstraintValidatorContext context;

    @BeforeEach
    void setUp() {
        validator = new ResourcesValidator();
        context = mock(ConstraintValidatorContext.class);
        var builder = mock(ConstraintValidatorContext.ConstraintViolationBuilder.class);
        when(context.buildConstraintViolationWithTemplate(anyString())).thenReturn(builder);
        when(builder.addConstraintViolation()).thenReturn(context);
    }

    @Test
    void testNullResources() {
        assertTrue(validator.isValid(null, context));
    }

    @Test
    void testNullLimits() {
        var dto = mock(ResourcesDto.class);
        when(dto.limits()).thenReturn(null);
        when(dto.requests()).thenReturn(Map.of("cpu", "1"));
        assertTrue(validator.isValid(dto, context));
    }

    @Test
    void testNullRequests() {
        var dto = mock(ResourcesDto.class);
        when(dto.limits()).thenReturn(Map.of("cpu", "2"));
        when(dto.requests()).thenReturn(null);
        assertTrue(validator.isValid(dto, context));
    }

    @Test
    void testNoCommonKeys() {
        var dto = mock(ResourcesDto.class);
        when(dto.limits()).thenReturn(Map.of("cpu", "2"));
        when(dto.requests()).thenReturn(Map.of("memory", "1"));
        assertTrue(validator.isValid(dto, context));
    }

    @Test
    void testLimitGreaterThanRequest() {
        var dto = mock(ResourcesDto.class);
        when(dto.limits()).thenReturn(Map.of("cpu", "2", "memory", "4"));
        when(dto.requests()).thenReturn(Map.of("cpu", "1", "memory", "2"));
        assertTrue(validator.isValid(dto, context));
    }

    @Test
    void testLimitEqualToRequest() {
        var dto = mock(ResourcesDto.class);
        when(dto.limits()).thenReturn(Map.of("cpu", "2", "memory", "4"));
        when(dto.requests()).thenReturn(Map.of("cpu", "2", "memory", "4"));
        assertTrue(validator.isValid(dto, context));
    }

    @Test
    void testLimitLessThanRequest() {
        var dto = mock(ResourcesDto.class);
        when(dto.limits()).thenReturn(Map.of("cpu", "1", "memory", "2"));
        when(dto.requests()).thenReturn(Map.of("cpu", "2", "memory", "2"));
        assertFalse(validator.isValid(dto, context));
        verify(context).disableDefaultConstraintViolation();
        verify(context).buildConstraintViolationWithTemplate(contains("cpu"));
    }

    @Test
    void testNonNumericValues() {
        var dto = mock(ResourcesDto.class);
        when(dto.limits()).thenReturn(Map.of("cpu", "2", "memory", "4"));
        when(dto.requests()).thenReturn(Map.of("cpu", "1m", "memory", "2"));
        assertFalse(validator.isValid(dto, context));
    }

    @Test
    void testEmptyStrings() {
        var dto = mock(ResourcesDto.class);
        when(dto.limits()).thenReturn(Map.of("cpu", "", "memory", "4"));
        when(dto.requests()).thenReturn(Map.of("cpu", "1", "memory", ""));
        assertFalse(validator.isValid(dto, context));
    }
}