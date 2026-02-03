package com.epam.aidial.deployment.manager.validation;

import com.epam.aidial.deployment.manager.web.validation.TopicsValidator;
import jakarta.validation.ConstraintValidatorContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
        assertTrue(validator.isValid(null, context));
        verifyNoInteractions(context);
    }

    @Test
    void shouldBeValidForNonBlankTrimmedTopicsWithinMaxLength() {
        List<String> topics = List.of("topic1", "another-topic", "t".repeat(255));

        assertTrue(validator.isValid(topics, context));
    }

    @Test
    void shouldBeInvalidWhenTopicIsBlank() {
        List<String> topics = List.of("valid", "   ");

        assertFalse(validator.isValid(topics, context));
    }

    @Test
    void shouldBeInvalidWhenTopicExceedsMaxLength() {
        String longTopic = "t".repeat(256);
        List<String> topics = List.of("valid", longTopic);

        assertFalse(validator.isValid(topics, context));
    }

    @Test
    void shouldBeInvalidWhenTopicHasLeadingOrTrailingSpaces() {
        List<String> topics = List.of(" valid", "valid ", "\tvalid", "valid\t");

        assertFalse(validator.isValid(topics, context));
    }
}

