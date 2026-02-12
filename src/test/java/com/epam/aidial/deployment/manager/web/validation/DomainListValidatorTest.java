package com.epam.aidial.deployment.manager.web.validation;

import jakarta.validation.ConstraintValidatorContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DomainListValidatorTest {

    private DomainListValidator domainValidator;
    private ConstraintValidatorContext context;

    @BeforeEach
    void setUp() {
        domainValidator = new DomainListValidator();
        context = mock(ConstraintValidatorContext.class);
        var builder = mock(ConstraintValidatorContext.ConstraintViolationBuilder.class);
        when(context.buildConstraintViolationWithTemplate(anyString())).thenReturn(builder);
    }

    @ParameterizedTest
    @MethodSource("validDomainsProvider")
    void validDomains_shouldReturnTrue(List<String> domains) {
        assertTrue(domainValidator.isValid(domains, context));
    }

    static Stream<Arguments> validDomainsProvider() {
        return Stream.of(
                arguments(List.of("example.com", "sub.domain.org")),
                arguments(List.of("one.two.three.domain.org")),
                arguments(List.of("my-domain123.net", "a.co")),
                arguments(List.of("registry.untrusted-qwe-int32.aws.sandbox.dial.io")),
                arguments(List.of("github.com", "index.docker.io", "auth.docker.io")),
                arguments(List.of("docker-images-prod.s3.dualstack.us-east-1.amazonaws.com")),
                arguments(List.of("deb.debian.org", "debian.map.fastlydns.net", "astral.sh")),
                arguments(List.of("untrusted-aks-int32-distribution-registry.s3.amazonaws.com")),
                arguments(List.of("files.pythonhosted.org", "toolbox-data.anchore.io")),
                arguments(List.of("*"))
        );
    }

    @Test
    void domainsWithSchema_shouldReturnFalse() {
        assertFalse(domainValidator.isValid(List.of("http://example.com"), context));
        verify(context).disableDefaultConstraintViolation();
        verify(context).buildConstraintViolationWithTemplate("domain 'http://example.com' is not a valid domain name");

        assertFalse(domainValidator.isValid(List.of("https://example.com"), context));
    }

    @Test
    void domainsWithPath_shouldReturnFalse() {
        assertFalse(domainValidator.isValid(List.of("example.com/path"), context));
        verify(context).disableDefaultConstraintViolation();
        verify(context).buildConstraintViolationWithTemplate("domain 'example.com/path' is not a valid domain name");

        assertFalse(domainValidator.isValid(List.of("sub.domain.org/another"), context));
    }

    @Test
    void domainsWithQuery_shouldReturnFalse() {
        assertFalse(domainValidator.isValid(List.of("example.com?param=value"), context));
        verify(context).disableDefaultConstraintViolation();
        verify(context).buildConstraintViolationWithTemplate("domain 'example.com?param=value' is not a valid domain name");

        assertFalse(domainValidator.isValid(List.of("domain.org?foo=bar"), context));
    }

    @Test
    void domainsWithFragment_shouldReturnFalse() {
        assertFalse(domainValidator.isValid(List.of("example.com#section"), context));
        verify(context).disableDefaultConstraintViolation();
        verify(context).buildConstraintViolationWithTemplate("domain 'example.com#section' is not a valid domain name");

        assertFalse(domainValidator.isValid(List.of("domain.org#top"), context));
    }

    @Test
    void domainsWithSpaces_shouldReturnFalse() {
        assertFalse(domainValidator.isValid(List.of("example .com"), context));
        verify(context).disableDefaultConstraintViolation();
        verify(context).buildConstraintViolationWithTemplate("domain 'example .com' is not a valid domain name");

        assertFalse(domainValidator.isValid(List.of(" example.com"), context));
        assertFalse(domainValidator.isValid(List.of("example.com "), context));
    }

    @Test
    void domainsWithInvalidCharacters_shouldReturnFalse() {
        assertFalse(domainValidator.isValid(List.of("exa$mple.com"), context));
        verify(context).disableDefaultConstraintViolation();
        verify(context).buildConstraintViolationWithTemplate("domain 'exa$mple.com' is not a valid domain name");

        assertFalse(domainValidator.isValid(List.of("example!.com"), context));
        assertFalse(domainValidator.isValid(List.of("*.example.com"), context));
        assertFalse(domainValidator.isValid(List.of(".example.com"), context));
    }

    @Test
    void domainsWithLeadingOrTrailingDash_shouldReturnFalse() {
        assertFalse(domainValidator.isValid(List.of("-example.com"), context));
        verify(context).disableDefaultConstraintViolation();
        verify(context).buildConstraintViolationWithTemplate("domain '-example.com' is not a valid domain name");

        assertFalse(domainValidator.isValid(List.of("example-.com"), context));
    }

    @Test
    void domainsWithConsecutiveDots_shouldReturnFalse() {
        assertFalse(domainValidator.isValid(List.of("example..com"), context));
        verify(context).disableDefaultConstraintViolation();
        verify(context).buildConstraintViolationWithTemplate("domain 'example..com' is not a valid domain name");

        assertFalse(domainValidator.isValid(List.of("next..example.com"), context));
    }

    @Test
    void nullOrEmptyDomain_shouldReturnFalse() {
        assertFalse(domainValidator.isValid(Collections.singletonList(null), context));
        verify(context).disableDefaultConstraintViolation();
        verify(context).buildConstraintViolationWithTemplate("domain must not be null");

        assertFalse(domainValidator.isValid(List.of(""), context));
    }

    @Test
    void domainsWithInvalidSize_shouldReturnFalse() {
        assertFalse(domainValidator.isValid(List.of("a.b"), context));
        verify(context).disableDefaultConstraintViolation();
        verify(context).buildConstraintViolationWithTemplate("domain 'a.b' must be between 4 and 253 characters long");

        setUp();
        String longDomain = "example.example.example.example.example.example.example.example.example.example.example."
                + "example.example.example.example.example.example.example.example.example.example.example."
                + "example.example.example.example.example.example.example.example.example.example.example.example.";
        assertFalse(domainValidator.isValid(List.of(longDomain), context));
        verify(context).disableDefaultConstraintViolation();
        verify(context).buildConstraintViolationWithTemplate(
                String.format("domain '%s' must be between 4 and 253 characters long", longDomain));
    }
}
