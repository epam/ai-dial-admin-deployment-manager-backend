package com.epam.aidial.deployment.manager.web.validation;

import com.epam.aidial.deployment.manager.web.dto.ScalingDto;
import com.epam.aidial.deployment.manager.web.dto.ScalingStrategyDto;
import com.epam.aidial.deployment.manager.web.dto.ScalingStrategyTypeDto;
import jakarta.validation.ConstraintValidatorContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ScalingValidatorTest {

    private ScalingValidator validator;
    private ConstraintValidatorContext context;

    @BeforeEach
    void setUp() {
        validator = new ScalingValidator();
        context = mock(ConstraintValidatorContext.class);
        ConstraintValidatorContext.ConstraintViolationBuilder builder =
                mock(ConstraintValidatorContext.ConstraintViolationBuilder.class);
        when(context.buildConstraintViolationWithTemplate(anyString())).thenReturn(builder);
        when(builder.addConstraintViolation()).thenReturn(context);
    }

    @Test
    void shouldBeValidWhenValueIsNull() {
        assertThat(validator.isValid(null, context)).isTrue();
        verifyNoInteractions(context);
    }

    @Test
    void shouldBeValidWhenMinLessThanMax() {
        ScalingDto dto = new ScalingDto();
        dto.setMinReplicas(1);
        dto.setMaxReplicas(2);
        dto.setStrategy(new ScalingStrategyDto(ScalingStrategyTypeDto.ACTIVE_REQUESTS, 50));

        assertThat(validator.isValid(dto, context)).isTrue();
        verifyNoInteractions(context);
    }

    @Test
    void shouldBeValidWhenMinEqualsMaxAndStrategyNull() {
        ScalingDto dto = new ScalingDto();
        dto.setMinReplicas(2);
        dto.setMaxReplicas(2);
        dto.setStrategy(null);

        assertThat(validator.isValid(dto, context)).isTrue();
        verifyNoInteractions(context);
    }

    @Test
    void shouldFailWhenMinEqualsMaxAndStrategySet() {
        ScalingDto dto = new ScalingDto();
        dto.setMinReplicas(2);
        dto.setMaxReplicas(2);
        dto.setStrategy(new ScalingStrategyDto(ScalingStrategyTypeDto.ACTIVE_REQUESTS, 10));

        assertThat(validator.isValid(dto, context)).isFalse();
        verify(context).buildConstraintViolationWithTemplate(
                "strategy must be null when minReplicas equals maxReplicas");
    }

    @Test
    void shouldFailWhenMinGreaterThanMax() {
        ScalingDto dto = new ScalingDto();
        dto.setMinReplicas(3);
        dto.setMaxReplicas(2);

        assertThat(validator.isValid(dto, context)).isFalse();
        verify(context).buildConstraintViolationWithTemplate(
                "minReplicas must be less than or equal to maxReplicas");
    }
}
