package com.epam.aidial.deployment.manager.web.validation;

import jakarta.validation.ConstraintValidatorContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;

class DomainListValidatorTest {

    private DomainListValidator domainValidator;
    private ConstraintValidatorContext context;

    @BeforeEach
    void setUp() {
        domainValidator = new DomainListValidator();
        context = mock(ConstraintValidatorContext.class);
    }


    @Test
    void domainsWithSchema_shouldReturnFalse() {
        assertFalse(domainValidator.isValid(List.of("http://example.com"), context));
        assertFalse(domainValidator.isValid(List.of("https://example.com"), context));
    }

    @Test
    void domainsWithPath_shouldReturnFalse() {
        assertFalse(domainValidator.isValid(List.of("example.com/path"), context));
        assertFalse(domainValidator.isValid(List.of("sub.domain.org/another"), context));
    }

    @Test
    void domainsWithQuery_shouldReturnFalse() {
        assertFalse(domainValidator.isValid(List.of("example.com?param=value"), context));
        assertFalse(domainValidator.isValid(List.of("domain.org?foo=bar"), context));
    }

    @Test
    void domainsWithFragment_shouldReturnFalse() {
        assertFalse(domainValidator.isValid(List.of("example.com#section"), context));
        assertFalse(domainValidator.isValid(List.of("domain.org#top"), context));
    }

    @Test
    void domainsWithSpaces_shouldReturnFalse() {
        assertFalse(domainValidator.isValid(List.of("example .com"), context));
        assertFalse(domainValidator.isValid(List.of(" example.com"), context));
        assertFalse(domainValidator.isValid(List.of("example.com "), context));
    }

    @Test
    void domainsWithInvalidCharacters_shouldReturnFalse() {
        assertFalse(domainValidator.isValid(List.of("exa$mple.com"), context));
        assertFalse(domainValidator.isValid(List.of("example!.com"), context));
        assertFalse(domainValidator.isValid(List.of("*.example.com"), context));
        assertFalse(domainValidator.isValid(List.of(".example.com"), context));
    }

    @Test
    void domainsWithLeadingOrTrailingDash_shouldReturnFalse() {
        assertFalse(domainValidator.isValid(List.of("-example.com"), context));
        assertFalse(domainValidator.isValid(List.of("example-.com"), context));
    }

    @Test
    void domainsWithConsecutiveDots_shouldReturnFalse() {
        assertFalse(domainValidator.isValid(List.of("example..com"), context));
        assertFalse(domainValidator.isValid(List.of("next..example.com"), context));
    }

    @Test
    void nullOrEmptyDomain_shouldReturnFalse() {
        assertFalse(domainValidator.isValid(Collections.singletonList(null), context));
        assertFalse(domainValidator.isValid(List.of(""), context));
    }
}
