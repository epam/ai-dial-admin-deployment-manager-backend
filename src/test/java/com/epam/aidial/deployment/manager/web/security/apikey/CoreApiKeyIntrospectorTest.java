package com.epam.aidial.deployment.manager.web.security.apikey;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.web.client.MockRestServiceServer;

import java.net.SocketTimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class CoreApiKeyIntrospectorTest {

    private static final String CORE_URL = "http://core";
    private static final String USER_INFO_URL = "http://core/v1/user/info";
    private static final String PRINCIPAL_CLAIM = "sub";
    private static final String EMAIL_CLAIM = "email";

    private ApiKeyProperties properties;
    private CoreApiKeyIntrospector introspector;
    private MockRestServiceServer mockServer;

    @BeforeEach
    void setUp() {
        properties = new ApiKeyProperties(new ObjectMapper(), "");
        properties.setEnabled(true);
        properties.setCoreUrl(CORE_URL);
        properties.setRequestTimeoutMs(1000);
        properties.setCacheTtlSeconds(60);
        properties.setCacheMaxSize(100);
        properties.setRolesMapping("{\"admin\":[\"FULL_ADMIN\"]}");
        properties.setStartupProbe(false);
        properties.validate();

        introspector = new CoreApiKeyIntrospector(new RestTemplateBuilder(), properties, PRINCIPAL_CLAIM, EMAIL_CLAIM);
        mockServer = MockRestServiceServer.bindTo(introspector.getRestTemplate()).build();
    }

    @Test
    void shouldReturnProjectKeyResultOnSuccess() {
        mockServer.expect(requestTo(USER_INFO_URL))
                .andExpect(method(GET))
                .andExpect(header("Api-Key", "valid-key"))
                .andRespond(withSuccess("{\"roles\":[\"admin\"],\"project\":\"acme\"}", MediaType.APPLICATION_JSON));

        IntrospectionResult result = introspector.introspect("valid-key");

        assertThat(result.principal()).isEqualTo("acme");
        assertThat(result.email()).isNull();
        assertThat(result.rawRoles()).containsExactly("admin");
        assertThat(result.fromProjectKey()).isTrue();
        mockServer.verify();
    }

    @Test
    void shouldReturnJwtRootResultWhenResponseHasUserClaims() {
        mockServer.expect(requestTo(USER_INFO_URL))
                .andRespond(withSuccess(
                        "{\"roles\":[\"sso-admin\"],\"userClaims\":{\"sub\":[\"user-123\"],\"email\":[\"u@example.com\"]}}",
                        MediaType.APPLICATION_JSON));

        IntrospectionResult result = introspector.introspect("per-request-key");

        assertThat(result.principal()).isEqualTo("user-123");
        assertThat(result.email()).isEqualTo("u@example.com");
        assertThat(result.rawRoles()).containsExactly("sso-admin");
        assertThat(result.fromProjectKey()).isFalse();
    }

    @Test
    void shouldAcceptUserClaimsWithScalarValues() {
        mockServer.expect(requestTo(USER_INFO_URL))
                .andRespond(withSuccess(
                        "{\"roles\":[\"sso-admin\"],\"userClaims\":{\"sub\":\"user-123\",\"email\":\"u@example.com\"}}",
                        MediaType.APPLICATION_JSON));

        IntrospectionResult result = introspector.introspect("per-request-key");

        assertThat(result.principal()).isEqualTo("user-123");
        assertThat(result.email()).isEqualTo("u@example.com");
        assertThat(result.fromProjectKey()).isFalse();
    }

    @Test
    void shouldAllowMissingEmailInUserClaims() {
        mockServer.expect(requestTo(USER_INFO_URL))
                .andRespond(withSuccess(
                        "{\"roles\":[\"sso-admin\"],\"userClaims\":{\"sub\":[\"user-123\"]}}",
                        MediaType.APPLICATION_JSON));

        IntrospectionResult result = introspector.introspect("per-request-key");

        assertThat(result.principal()).isEqualTo("user-123");
        assertThat(result.email()).isNull();
        assertThat(result.fromProjectKey()).isFalse();
    }

    @Test
    void shouldPreferProjectOverUserClaimsWhenBothPresent() {
        // Core's UserInfoController never emits both, but be defensive.
        mockServer.expect(requestTo(USER_INFO_URL))
                .andRespond(withSuccess(
                        "{\"roles\":[\"admin\"],\"project\":\"acme\",\"userClaims\":{\"sub\":[\"user-123\"]}}",
                        MediaType.APPLICATION_JSON));

        IntrospectionResult result = introspector.introspect("ambiguous");
        assertThat(result.principal()).isEqualTo("acme");
        assertThat(result.fromProjectKey()).isTrue();
    }

    @Test
    void shouldThrowBadCredentialsOnHttp401() {
        mockServer.expect(requestTo(USER_INFO_URL))
                .andRespond(withStatus(UNAUTHORIZED));

        assertThatThrownBy(() -> introspector.introspect("bad-key"))
                .isInstanceOf(BadCredentialsException.class);
        mockServer.verify();
    }

    @Test
    void shouldThrowBadCredentialsWhenResponseHasNeitherProjectNorUserClaims() {
        mockServer.expect(requestTo(USER_INFO_URL))
                .andRespond(withSuccess("{\"roles\":[\"admin\"]}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> introspector.introspect("key"))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void shouldThrowBadCredentialsWhenUserClaimsLacksPrincipalClaim() {
        mockServer.expect(requestTo(USER_INFO_URL))
                .andRespond(withSuccess(
                        "{\"roles\":[\"sso-admin\"],\"userClaims\":{\"email\":[\"u@example.com\"]}}",
                        MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> introspector.introspect("per-request-key"))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void shouldThrowBadCredentialsOnEmptyUserClaims() {
        mockServer.expect(requestTo(USER_INFO_URL))
                .andRespond(withSuccess(
                        "{\"roles\":[\"sso-admin\"],\"userClaims\":{}}",
                        MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> introspector.introspect("per-request-key"))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void shouldThrowAuthenticationServiceExceptionOnConnectionFailure() {
        mockServer.expect(requestTo(USER_INFO_URL))
                .andRespond(withException(new SocketTimeoutException("connect timeout")));

        assertThatThrownBy(() -> introspector.introspect("key"))
                .isInstanceOf(AuthenticationServiceException.class);
    }

    @Test
    void shouldTolerateMissingRolesField() {
        mockServer.expect(requestTo(USER_INFO_URL))
                .andRespond(withSuccess("{\"project\":\"acme\"}", MediaType.APPLICATION_JSON));

        IntrospectionResult result = introspector.introspect("key");
        assertThat(result.principal()).isEqualTo("acme");
        assertThat(result.rawRoles()).isEmpty();
        assertThat(result.fromProjectKey()).isTrue();
    }

    @Test
    void shouldParseJsonBodyServedAsOctetStream() {
        mockServer.expect(requestTo(USER_INFO_URL))
                .andExpect(method(GET))
                .andExpect(header("Api-Key", "valid-key"))
                .andExpect(header("Accept", MediaType.APPLICATION_JSON_VALUE))
                .andRespond(withSuccess("{\"roles\":[\"admin\"],\"project\":\"acme\"}", MediaType.APPLICATION_OCTET_STREAM));

        IntrospectionResult result = introspector.introspect("valid-key");

        assertThat(result.principal()).isEqualTo("acme");
        assertThat(result.rawRoles()).containsExactly("admin");
        assertThat(result.fromProjectKey()).isTrue();
        mockServer.verify();
    }

    @Test
    void probeShouldSucceedWhenCoreRespondsWith4xx() {
        properties.setStartupProbe(true);
        mockServer.expect(requestTo(USER_INFO_URL))
                .andExpect(header("Api-Key", "dm-startup-probe"))
                .andRespond(withStatus(UNAUTHORIZED));

        assertThatCode(introspector::probeCore).doesNotThrowAnyException();
        mockServer.verify();
    }

    @Test
    void probeShouldFailWhenCoreUnreachable() {
        properties.setStartupProbe(true);
        mockServer.expect(requestTo(USER_INFO_URL))
                .andRespond(withException(new SocketTimeoutException("connect timeout")));

        assertThatThrownBy(introspector::probeCore)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("unreachable");
    }

    @Test
    void probeShouldFailWhenCoreResponds5xx() {
        properties.setStartupProbe(true);
        mockServer.expect(requestTo(USER_INFO_URL))
                .andRespond(withStatus(INTERNAL_SERVER_ERROR));

        assertThatThrownBy(introspector::probeCore)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("500");
    }

    @Test
    void probeShouldNoOpWhenDisabled() {
        properties.setStartupProbe(false);
        // No mockServer expectation set; probeCore must make no HTTP call.
        assertThatCode(introspector::probeCore).doesNotThrowAnyException();
        mockServer.verify();
    }
}
