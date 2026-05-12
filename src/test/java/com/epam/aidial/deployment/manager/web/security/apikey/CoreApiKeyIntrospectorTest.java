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

    private static final String CORE_URL = "http://core/v1/user/info";

    private ApiKeyProperties properties;
    private CoreApiKeyIntrospector introspector;
    private MockRestServiceServer mockServer;

    @BeforeEach
    void setUp() {
        properties = new ApiKeyProperties(new ObjectMapper());
        properties.setEnabled(true);
        properties.setCoreUserInfoUrl(CORE_URL);
        properties.setRequestTimeoutMs(1000);
        properties.setCacheTtlSeconds(60);
        properties.setCacheMaxSize(100);
        properties.setStartupProbe(false);
        properties.validate();

        introspector = new CoreApiKeyIntrospector(new RestTemplateBuilder(), properties);
        mockServer = MockRestServiceServer.bindTo(introspector.getRestTemplate()).build();
    }

    @Test
    void shouldReturnIntrospectionResultOnSuccess() {
        mockServer.expect(requestTo(CORE_URL))
                .andExpect(method(GET))
                .andExpect(header("Api-Key", "valid-key"))
                .andRespond(withSuccess("{\"roles\":[\"admin\"],\"project\":\"acme\"}", MediaType.APPLICATION_JSON));

        IntrospectionResult result = introspector.introspect("valid-key");

        assertThat(result.project()).isEqualTo("acme");
        assertThat(result.rawRoles()).containsExactly("admin");
        mockServer.verify();
    }

    @Test
    void shouldThrowBadCredentialsOnHttp401() {
        mockServer.expect(requestTo(CORE_URL))
                .andRespond(withStatus(UNAUTHORIZED));

        assertThatThrownBy(() -> introspector.introspect("bad-key"))
                .isInstanceOf(BadCredentialsException.class);
        mockServer.verify();
    }

    @Test
    void shouldThrowBadCredentialsOnMissingProjectField() {
        mockServer.expect(requestTo(CORE_URL))
                .andRespond(withSuccess("{\"roles\":[\"admin\"]}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> introspector.introspect("key"))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void shouldThrowAuthenticationServiceExceptionOnConnectionFailure() {
        mockServer.expect(requestTo(CORE_URL))
                .andRespond(withException(new SocketTimeoutException("connect timeout")));

        assertThatThrownBy(() -> introspector.introspect("key"))
                .isInstanceOf(AuthenticationServiceException.class);
    }

    @Test
    void shouldTolerateMissingRolesField() {
        mockServer.expect(requestTo(CORE_URL))
                .andRespond(withSuccess("{\"project\":\"acme\"}", MediaType.APPLICATION_JSON));

        IntrospectionResult result = introspector.introspect("key");
        assertThat(result.project()).isEqualTo("acme");
        assertThat(result.rawRoles()).isEmpty();
    }

    @Test
    void shouldParseJsonBodyServedAsOctetStream() {
        mockServer.expect(requestTo(CORE_URL))
                .andExpect(method(GET))
                .andExpect(header("Api-Key", "valid-key"))
                .andExpect(header("Accept", MediaType.APPLICATION_JSON_VALUE))
                .andRespond(withSuccess("{\"roles\":[\"admin\"],\"project\":\"acme\"}", MediaType.APPLICATION_OCTET_STREAM));

        IntrospectionResult result = introspector.introspect("valid-key");

        assertThat(result.project()).isEqualTo("acme");
        assertThat(result.rawRoles()).containsExactly("admin");
        mockServer.verify();
    }

    @Test
    void probeShouldSucceedWhenCoreRespondsWith4xx() {
        properties.setStartupProbe(true);
        mockServer.expect(requestTo(CORE_URL))
                .andExpect(header("Api-Key", "dm-startup-probe"))
                .andRespond(withStatus(UNAUTHORIZED));

        assertThatCode(introspector::probeCore).doesNotThrowAnyException();
        mockServer.verify();
    }

    @Test
    void probeShouldFailWhenCoreUnreachable() {
        properties.setStartupProbe(true);
        mockServer.expect(requestTo(CORE_URL))
                .andRespond(withException(new SocketTimeoutException("connect timeout")));

        assertThatThrownBy(introspector::probeCore)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("unreachable");
    }

    @Test
    void probeShouldFailWhenCoreResponds5xx() {
        properties.setStartupProbe(true);
        mockServer.expect(requestTo(CORE_URL))
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
