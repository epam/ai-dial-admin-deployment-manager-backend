package com.epam.aidial.deployment.manager.web.validation;

import com.epam.aidial.deployment.manager.web.dto.ResourcesDto;
import jakarta.validation.ConstraintValidatorContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.util.ReflectionTestUtils.setField;

class ResourcesValidatorTest {

    private static final String CPU = "cpu";
    private static final String MEMORY = "memory";
    private static final String NVIDIA_GPU = "nvidia.com/gpu";

    private ResourcesValidator validator;
    private ConstraintValidatorContext context;

    @BeforeEach
    void setUp() {
        validator = new ResourcesValidator();
        // inject test configuration values
        setField(validator, "maxCpuResourceLimitInCores", 10d);
        setField(validator, "maxMemoryResourceLimitInMb", 100_000d);
        setField(validator, "maxNvidiaGpuResourceLimit", 5d);
        context = mock(ConstraintValidatorContext.class);
        var builder = mock(ConstraintValidatorContext.ConstraintViolationBuilder.class);
        when(context.buildConstraintViolationWithTemplate(anyString())).thenReturn(builder);
        when(builder.addConstraintViolation()).thenReturn(context);
    }

    @Test
    void testNullResources() {
        assertThat(validator.isValid(null, context)).isTrue();
    }

    @Test
    void testNullLimits() {
        var dto = mock(ResourcesDto.class);
        when(dto.limits()).thenReturn(null);
        when(dto.requests()).thenReturn(Map.of(CPU, "1"));
        assertThat(validator.isValid(dto, context)).isTrue();
    }

    @Test
    void testNullRequests() {
        var dto = mock(ResourcesDto.class);
        when(dto.limits()).thenReturn(Map.of(CPU, "2"));
        when(dto.requests()).thenReturn(null);
        assertThat(validator.isValid(dto, context)).isTrue();
    }

    @Test
    void testNoCommonKeys() {
        var dto = mock(ResourcesDto.class);
        when(dto.limits()).thenReturn(Map.of(CPU, "2"));
        when(dto.requests()).thenReturn(Map.of(MEMORY, "1"));
        assertThat(validator.isValid(dto, context)).isTrue();
    }

    @Test
    void testLimitGreaterThanRequest() {
        var dto = mock(ResourcesDto.class);
        when(dto.limits()).thenReturn(Map.of(CPU, "2", MEMORY, "4"));
        when(dto.requests()).thenReturn(Map.of(CPU, "1", MEMORY, "2"));
        assertThat(validator.isValid(dto, context)).isTrue();
    }

    @Test
    void testLimitEqualToRequest() {
        var dto = mock(ResourcesDto.class);
        when(dto.limits()).thenReturn(Map.of(CPU, "0.5", MEMORY, "4", NVIDIA_GPU, "0"));
        when(dto.requests()).thenReturn(Map.of(CPU, "0.5", MEMORY, "4", NVIDIA_GPU, "0"));
        assertThat(validator.isValid(dto, context)).isTrue();
    }

    @Test
    void testLimitLessThanRequest() {
        var dto = mock(ResourcesDto.class);
        when(dto.limits()).thenReturn(Map.of(CPU, "1", MEMORY, "2"));
        when(dto.requests()).thenReturn(Map.of(CPU, "2", MEMORY, "2"));
        assertThat(validator.isValid(dto, context)).isFalse();
        verify(context).disableDefaultConstraintViolation();
        verify(context).buildConstraintViolationWithTemplate(contains(CPU));
    }

    @Test
    void testNonNumericValues() {
        var dto = mock(ResourcesDto.class);
        when(dto.limits()).thenReturn(Map.of(CPU, "2", MEMORY, "4"));
        when(dto.requests()).thenReturn(Map.of(CPU, "1m", MEMORY, "2"));
        assertThat(validator.isValid(dto, context)).isFalse();
    }

    @Test
    void testEmptyStrings() {
        var dto = mock(ResourcesDto.class);
        when(dto.limits()).thenReturn(Map.of(CPU, "", MEMORY, "4"));
        when(dto.requests()).thenReturn(Map.of(CPU, "1", MEMORY, ""));
        assertThat(validator.isValid(dto, context)).isFalse();
    }

    @Test
    void testValuesMustBeGreaterThanZero() {
        var dto = mock(ResourcesDto.class);
        when(dto.limits()).thenReturn(Map.of(CPU, "0", MEMORY, "4"));
        when(dto.requests()).thenReturn(Map.of(CPU, "1", MEMORY, "2"));

        assertThat(validator.isValid(dto, context)).isFalse();
        verify(context).disableDefaultConstraintViolation();
        verify(context).buildConstraintViolationWithTemplate(contains("must be greater than 0"));
    }

    @Test
    void testGpuValuesMustBeGreaterThanOrEqualToZero() {
        var dto = mock(ResourcesDto.class);
        when(dto.limits()).thenReturn(Map.of(NVIDIA_GPU, "0"));
        when(dto.requests()).thenReturn(Map.of(NVIDIA_GPU, "-1"));

        assertThat(validator.isValid(dto, context)).isFalse();
        verify(context).disableDefaultConstraintViolation();
        verify(context).buildConstraintViolationWithTemplate(contains("must be greater than or equal to 0"));
    }

    @Test
    void testCpuExceedsConfiguredLimit() {
        var dto = mock(ResourcesDto.class);
        when(dto.limits()).thenReturn(Map.of(CPU, "11"));
        when(dto.requests()).thenReturn(Map.of(CPU, "5"));

        assertThat(validator.isValid(dto, context)).isFalse();
        verify(context).disableDefaultConstraintViolation();
        verify(context).buildConstraintViolationWithTemplate(contains("must not exceed"));
    }

    @Test
    void testMemoryExceedsConfiguredLimit() {
        var dto = mock(ResourcesDto.class);
        // maxMemoryResourceLimitInMb = 100_000 => bytes = 100_000_000_000
        String overLimit = String.valueOf(100_000_000_020L);
        when(dto.limits()).thenReturn(Map.of(MEMORY, overLimit));
        when(dto.requests()).thenReturn(Map.of(MEMORY, "1000"));

        assertThat(validator.isValid(dto, context)).isFalse();
        verify(context).disableDefaultConstraintViolation();
        verify(context).buildConstraintViolationWithTemplate(contains("must not exceed"));
    }

    @Test
    void testNvidiaExceedsConfiguredLimit() {
        var dto = mock(ResourcesDto.class);
        when(dto.limits()).thenReturn(Map.of(NVIDIA_GPU, "20"));
        when(dto.requests()).thenReturn(Map.of(NVIDIA_GPU, "5"));

        assertThat(validator.isValid(dto, context)).isFalse();
        verify(context).disableDefaultConstraintViolation();
        verify(context).buildConstraintViolationWithTemplate(contains("must not exceed"));
    }
}