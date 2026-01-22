package com.epam.aidial.deployment.manager.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DomainValidatorTest {

    private DomainValidator domainValidator;

    @BeforeEach
    void setUp() {
        domainValidator = new DomainValidator();
    }

    @Test
    void validDomains_shouldReturnTrue() {
        assertTrue(domainValidator.isValid("example.com"));
        assertTrue(domainValidator.isValid("sub.domain.org"));
        assertTrue(domainValidator.isValid("one.two.three.domain.org"));
        assertTrue(domainValidator.isValid("my-domain123.net"));
        assertTrue(domainValidator.isValid("a.co"));
    }

    @Test
    void domainsWithSchema_shouldReturnFalse() {
        assertFalse(domainValidator.isValid("http://example.com"));
        assertFalse(domainValidator.isValid("https://example.com"));
    }

    @Test
    void domainsWithPath_shouldReturnFalse() {
        assertFalse(domainValidator.isValid("example.com/path"));
        assertFalse(domainValidator.isValid("sub.domain.org/another"));
    }

    @Test
    void domainsWithQuery_shouldReturnFalse() {
        assertFalse(domainValidator.isValid("example.com?param=value"));
        assertFalse(domainValidator.isValid("domain.org?foo=bar"));
    }

    @Test
    void domainsWithFragment_shouldReturnFalse() {
        assertFalse(domainValidator.isValid("example.com#section"));
        assertFalse(domainValidator.isValid("domain.org#top"));
    }

    @Test
    void domainsWithSpaces_shouldReturnFalse() {
        assertFalse(domainValidator.isValid("example .com"));
        assertFalse(domainValidator.isValid(" example.com"));
        assertFalse(domainValidator.isValid("example.com "));
    }

    @Test
    void domainsWithInvalidCharacters_shouldReturnFalse() {
        assertFalse(domainValidator.isValid("exa$mple.com"));
        assertFalse(domainValidator.isValid("example!.com"));
        assertFalse(domainValidator.isValid("*.example.com"));
        assertFalse(domainValidator.isValid(".example.com"));
    }

    @Test
    void domainsWithLeadingOrTrailingDash_shouldReturnFalse() {
        assertFalse(domainValidator.isValid("-example.com"));
        assertFalse(domainValidator.isValid("example-.com"));
    }

    @Test
    void domainsWithConsecutiveDots_shouldReturnFalse() {
        assertFalse(domainValidator.isValid("example..com"));
        assertFalse(domainValidator.isValid("next..example.com"));
    }

    @Test
    void nullOrEmptyDomain_shouldReturnFalse() {
        assertFalse(domainValidator.isValid(null));
        assertFalse(domainValidator.isValid(""));
    }
}
