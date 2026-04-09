package com.epam.aidial.deployment.manager.web.validation;

import jakarta.validation.ConstraintValidatorContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class TopicsValidatorTest {

    private TopicsValidator validator;
    private ConstraintValidatorContext context;

    @BeforeEach
    void setUp() {
        validator = new TopicsValidator();
        context = mock(ConstraintValidatorContext.class);
    }

    @Test
    void shouldBeValidWhenTopicsNull() {
        assertThat(validator.isValid(null, context)).isTrue();
        verifyNoInteractions(context);
    }

    @Test
    void shouldBeValidForNonBlankTrimmedTopicsWithinMaxLength() {
        List<String> topics = List.of("topic1", "another-topic", "t".repeat(255));

        assertThat(validator.isValid(topics, context)).isTrue();
    }

    @Test
    void shouldBeInvalidWhenTopicIsBlank() {
        List<String> topics = List.of("valid", "   ");

        assertThat(validator.isValid(topics, context)).isFalse();
    }

    @Test
    void shouldBeInvalidWhenTopicExceedsMaxLength() {
        String longTopic = "t".repeat(256);
        List<String> topics = List.of("valid", longTopic);

        assertThat(validator.isValid(topics, context)).isFalse();
    }

    @Test
    void shouldBeInvalidWhenTopicHasLeadingOrTrailingSpaces() {
        List<String> topics = List.of(" valid", "valid ", "\tvalid", "valid\t");

        assertThat(validator.isValid(topics, context)).isFalse();
    }
}

