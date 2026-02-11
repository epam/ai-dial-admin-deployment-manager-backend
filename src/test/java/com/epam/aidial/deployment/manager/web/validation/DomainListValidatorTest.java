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
import static org.mockito.Mockito.mock;

class DomainListValidatorTest {

    private DomainListValidator domainValidator;
    private ConstraintValidatorContext context;

    @BeforeEach
    void setUp() {
        domainValidator = new DomainListValidator();
        context = mock(ConstraintValidatorContext.class);
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

    @Test
    void domainsWithInvalidSize_shouldReturnFalse() {
        assertFalse(domainValidator.isValid(List.of("a.b"), context));
        assertFalse(domainValidator.isValid(List.of(
                "example.example.example.example.example.example.example.example.example.example.example."
                    + "example.example.example.example.example.example.example.example.example.example.example."
                    + "example.example.example.example.example.example.example.example.example.example.example.example."),
                context));
    }
}
