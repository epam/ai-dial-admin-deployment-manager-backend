package com.epam.aidial.deployment.manager.web.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import org.apache.commons.lang3.StringUtils;

import java.util.List;

/**
 * Validator for topics ensuring each topic is not blank, doesn't exceed 255 characters
 * and doesn't have leading/trailing spaces.
 */
public class TopicsValidator implements ConstraintValidator<ValidTopics, List<String>> {

    private static final int MAX_TOPIC_LENGTH = 255;

    @Override
    public boolean isValid(List<String> topics, ConstraintValidatorContext context) {
        if (topics == null) {
            return true; // Let @NotNull handle null validation
        }

        for (String topic : topics) {
            if (StringUtils.isBlank(topic) || topic.length() > MAX_TOPIC_LENGTH) {
                return false;
            }
            if (!topic.equals(topic.trim())) {
                return false;
            }
        }

        return true;
    }
}