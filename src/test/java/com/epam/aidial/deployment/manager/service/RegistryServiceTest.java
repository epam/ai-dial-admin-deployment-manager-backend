package com.epam.aidial.deployment.manager.service;

import com.epam.aidial.deployment.manager.configuration.DockerAuthScheme;
import com.epam.aidial.deployment.manager.configuration.RegistryProperties;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.Test;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

class RegistryServiceTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static String expectedBase64(String user, String pass) {
        return Base64.getEncoder().encodeToString((user + ":" + pass).getBytes(StandardCharsets.UTF_8));
    }

    private RegistryService newService(String registry, URI registryProtocol, String imageFormat, DockerAuthScheme authScheme,
                                       String user, String password, String trustedPrivateRegistries) {
        RegistryProperties registryProperties = new RegistryProperties();
        registryProperties.setUrl(registry);
        registryProperties.setProtocol(registryProtocol);
        registryProperties.setAuth(authScheme);
        registryProperties.setUser(user);
        registryProperties.setPassword(password);
        
        if (StringUtils.isBlank(trustedPrivateRegistries)) {
            registryProperties.setTrustedPrivateRegistries(new ArrayList<>());
        } else {
            try {
                List<RegistryProperties.TrustedPrivateRegistry> registries = MAPPER.readValue(
                        trustedPrivateRegistries, new TypeReference<>() {}
                );
                registryProperties.setTrustedPrivateRegistries(registries);
            } catch (Exception e) {
                throw new IllegalArgumentException("Invalid JSON format for trusted-private-registries: " + e.getMessage(), e);
            }
        }

        return new RegistryService(registryProperties, imageFormat);
    }

    @Test
    void validate_shouldThrow_whenBasicAndUserBlank() {
        RegistryService service = newService("my.registry", URI.create("https"), "%s", DockerAuthScheme.BASIC, "   ", "pass", null);

        IllegalStateException ex = catchThrowableOfType(service::validate, IllegalStateException.class);

        assertThat(ex).isInstanceOf(IllegalStateException.class).hasMessageContaining("User and password are required for BASIC docker registry authentication.");
    }

    @Test
    void validate_shouldThrow_whenBasicAndPasswordNull() {
        RegistryService service = newService("my.registry", URI.create("https"), "%s", DockerAuthScheme.BASIC, "user", null, null);

        IllegalStateException ex = catchThrowableOfType(service::validate, IllegalStateException.class);

        assertThat(ex).isInstanceOf(IllegalStateException.class).hasMessageContaining("User and password are required for BASIC docker registry authentication.");
    }

    @Test
    void validate_shouldPass_whenBasicAndCredentialsPresent() {
        RegistryService service = newService("my.registry", URI.create("https"), "%s", DockerAuthScheme.BASIC, "user", "pass", null);

        assertThatCode(service::validate).doesNotThrowAnyException();
    }

    @Test
    void dockerConfig_shouldContainMainRegistryAuth_whenBasic() throws Exception {
        String registry = "my.registry:5000";
        URI protocol = URI.create("https");
        String user = "user";
        String pass = "p@ss:word";

        RegistryService service = newService(registry, protocol, "%s", DockerAuthScheme.BASIC, user, pass, null);

        String json = service.dockerConfig();

        Map<String, Object> root = MAPPER.readValue(json, new TypeReference<>() {
        });
        assertThat(root).containsKey("auths");

        Map<String, Object> auths = (Map<String, Object>) root.get("auths");
        String expectedKey = "%s://%s/v2".formatted(protocol.toString(), registry);

        assertThat(auths).containsKey(expectedKey);

        Map<String, String> authCfg = (Map<String, String>) auths.get(expectedKey);
        assertThat(authCfg.get("auth")).isEqualTo(expectedBase64(user, pass));
    }

    @Test
    void dockerConfig_shouldIncludeTrustedPrivateRegistries_whenProvided() throws Exception {
        String mainRegistry = "main.registry";
        URI protocol = URI.create("https");

        String trusted = """
                [
                  {"registry":"priv1.example.com","authScheme":"BASIC","user":"u1","password":"p1","protocol":"http"},
                  {"registry":"priv2.example.com","authScheme":"BASIC","user":"u2","password":"p2"},
                  {"registry":"ignored.example.com","authScheme":"TOKEN","user":"x","password":"y"},
                  {"registry":"skip.example.com","authScheme":"BASIC","user":"", "password":"p3"}
                ]
                """;

        RegistryService service = newService(mainRegistry, protocol, "%s", DockerAuthScheme.BASIC, "main", "mPass", trusted);

        String json = service.dockerConfig();

        Map<String, Object> root = MAPPER.readValue(json, new TypeReference<>() {
        });
        Map<String, Object> auths = (Map<String, Object>) root.get("auths");

        String mainKey = "%s://%s/v2".formatted(protocol.toString(), mainRegistry);

        assertThat(auths).containsKey(mainKey);
        assertThat(auths.get(mainKey)).extracting("auth").isEqualTo(expectedBase64("main", "mPass"));

        // Trusted registry 1 (http protocol specified)
        String priv1Key = "http://priv1.example.com/v2";
        assertThat(auths).containsKey(priv1Key);
        assertThat(auths.get(priv1Key)).extracting("auth").isEqualTo(expectedBase64("u1", "p1"));

        // Trusted registry 2 (default https)
        String priv2Key = "https://priv2.example.com/v2";
        assertThat(auths).containsKey(priv2Key);
        assertThat(auths.get(priv2Key)).extracting("auth").isEqualTo(expectedBase64("u2", "p2"));

        // TOKEN authScheme should be ignored
        assertThat(auths).doesNotContainKey("https://ignored.example.com/v2");

        // BASIC with blank user should be ignored
        assertThat(auths).doesNotContainKey("https://skip.example.com/v2");
    }

    @Test
    void dockerConfig_shouldUseDockerHubLegacyKey_forDockerHubAliases() throws Exception {
        // BuildKit's auth lookup hardcodes "https://index.docker.io/v1/" for the Docker Hub
        // Official index (see docker/cli's getAuthConfigKey). A "/v2"-shaped key for docker.io
        // is silently ignored there. Skopeo normalizes both forms.
        String trusted = """
                [
                  {"registry":"docker.io","authScheme":"BASIC","user":"u1","password":"p1"},
                  {"registry":"index.docker.io","authScheme":"BASIC","user":"u2","password":"p2"},
                  {"registry":"registry-1.docker.io","authScheme":"BASIC","user":"u3","password":"p3","protocol":"http"}
                ]
                """;

        RegistryService service = newService("main.registry", URI.create("https"), "%s",
                DockerAuthScheme.BASIC, "main", "mPass", trusted);

        String json = service.dockerConfig();

        Map<String, Object> root = MAPPER.readValue(json, new TypeReference<>() {
        });
        Map<String, Object> auths = (Map<String, Object>) root.get("auths");

        // All three Docker Hub aliases collapse to the single legacy key
        assertThat(auths).containsKey("https://index.docker.io/v1/");

        // Map-key collision: only one of the three entries' credentials survives. Assert the value
        // belongs to *some* configured entry rather than e.g. silently picking up the main-registry
        // credential — the actual winner is HashMap-order-dependent and not contractual.
        Map<String, String> hubAuth = (Map<String, String>) auths.get("https://index.docker.io/v1/");
        assertThat(hubAuth.get("auth")).isIn(
                expectedBase64("u1", "p1"),
                expectedBase64("u2", "p2"),
                expectedBase64("u3", "p3"));

        // /v2-shaped keys for Docker Hub must NOT be emitted, regardless of the entry's protocol field
        assertThat(auths).doesNotContainKey("https://docker.io/v2");
        assertThat(auths).doesNotContainKey("https://index.docker.io/v2");
        assertThat(auths).doesNotContainKey("https://registry-1.docker.io/v2");
        assertThat(auths).doesNotContainKey("http://registry-1.docker.io/v2");

        // Non-Hub registries keep the /v2 format
        assertThat(auths).containsKey("https://main.registry/v2");
    }

    @Test
    void dockerConfig_shouldThrowOnInvalidJson_whenTrustedRegistriesInvalid() {
        String mainRegistry = "main.registry";
        URI protocol = URI.create("https");

        String badTrusted = "not-json";

        IllegalArgumentException ex = catchThrowableOfType(
                () -> newService(mainRegistry, protocol, "%s", DockerAuthScheme.BASIC, "main", "mPass", badTrusted),
                IllegalArgumentException.class
        );

        assertThat(ex).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid JSON format for trusted-private-registries");
    }

    @Test
    void dockerConfig_shouldMatchExpectedJsonConstant() throws Exception {
        String mainRegistry = "main.registry";
        URI protocol = URI.create("https");

        String trusted = """
                [
                  {"registry":"priv1.example.com","authScheme":"BASIC","user":"u1","password":"p1","protocol":"http"},
                  {"registry":"priv2.example.com","authScheme":"BASIC","user":"u2","password":"p2"}
                ]
                """;

        RegistryService service = newService(mainRegistry, protocol, "%s", DockerAuthScheme.BASIC, "main", "mPass", trusted);

        String actualJson = service.dockerConfig();

        // Expected JSON
        String expectedJson = """
                {
                  "auths": {
                    "https://main.registry/v2": { "auth": "bWFpbjptUGFzcw==" },
                    "http://priv1.example.com/v2": { "auth": "dTE6cDE=" },
                    "https://priv2.example.com/v2": { "auth": "dTI6cDI=" }
                  }
                }
                """;

        assertThat(MAPPER.readTree(actualJson)).isEqualTo(MAPPER.readTree(expectedJson));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> authsOf(String dockerConfigJson) throws Exception {
        Map<String, Object> root = MAPPER.readValue(dockerConfigJson, new TypeReference<>() {
        });
        return (Map<String, Object>) root.get("auths");
    }

    @Test
    void dockerConfigForImages_shouldContainOnlyMainRegistry_whenImageIsInBasicAuthMainRegistry() throws Exception {
        RegistryService service = newService("main.registry", URI.create("https"), "%s",
                DockerAuthScheme.BASIC, "user", "pass", null);

        Optional<String> result = service.dockerConfigForImages(List.of("main.registry/app:1"));

        assertThat(result).isPresent();
        Map<String, Object> auths = authsOf(result.get());
        assertThat(auths).containsOnlyKeys("https://main.registry/v2");
        assertThat(auths.get("https://main.registry/v2")).extracting("auth").isEqualTo(expectedBase64("user", "pass"));
    }

    @Test
    void dockerConfigForImages_shouldContainOnlyMatchedTrustedRegistry_notTheMainRegistry() throws Exception {
        // The narrowing guarantee: a deployment whose image is in priv.example.com gets a secret with
        // ONLY that registry's credential — never the main registry's (least-privilege).
        String trusted = """
                [ {"registry":"priv.example.com","authScheme":"BASIC","user":"u1","password":"p1"} ]
                """;
        RegistryService service = newService("main.registry", URI.create("https"), "%s",
                DockerAuthScheme.BASIC, "main", "mPass", trusted);

        Optional<String> result = service.dockerConfigForImages(List.of("priv.example.com/app:1"));

        assertThat(result).isPresent();
        Map<String, Object> auths = authsOf(result.get());
        assertThat(auths).containsOnlyKeys("https://priv.example.com/v2");
        assertThat(auths.get("https://priv.example.com/v2")).extracting("auth").isEqualTo(expectedBase64("u1", "p1"));
        assertThat(auths).doesNotContainKey("https://main.registry/v2");
    }

    @Test
    void dockerConfigForImages_shouldBeEmpty_whenHostIsUnconfigured() {
        RegistryService service = newService("main.registry", URI.create("https"), "%s",
                DockerAuthScheme.BASIC, "user", "pass", null);

        assertThat(service.dockerConfigForImages(List.of("other.registry/app:1"))).isEmpty();
    }

    @Test
    void dockerConfigForImages_shouldBeEmpty_whenMainRegistryIsAnonymous() {
        RegistryService service = newService("main.registry", URI.create("https"), "%s",
                DockerAuthScheme.NONE, null, null, null);

        assertThat(service.dockerConfigForImages(List.of("main.registry/app:1"))).isEmpty();
    }

    @Test
    void dockerConfigForImages_shouldBeEmpty_whenTrustedRegistryIsNotBasic() {
        String trusted = """
                [ {"registry":"priv.example.com","authScheme":"TOKEN","user":"u1","password":"p1"} ]
                """;
        RegistryService service = newService("main.registry", URI.create("https"), "%s",
                DockerAuthScheme.NONE, null, null, trusted);

        assertThat(service.dockerConfigForImages(List.of("priv.example.com/app:1"))).isEmpty();
    }

    @Test
    void dockerConfigForImages_shouldNormalizeDockerHubAliases() throws Exception {
        // Credential configured under one Docker Hub alias must match an image expressed under another.
        String trusted = """
                [ {"registry":"docker.io","authScheme":"BASIC","user":"u1","password":"p1"} ]
                """;
        RegistryService service = newService("main.registry", URI.create("https"), "%s",
                DockerAuthScheme.NONE, null, null, trusted);

        Optional<String> result = service.dockerConfigForImages(List.of("registry-1.docker.io/library/app:1"));

        assertThat(result).isPresent();
        assertThat(authsOf(result.get())).containsOnlyKeys("https://index.docker.io/v1/");
    }

    @Test
    void dockerConfigForImages_shouldBeEmpty_whenReferenceIsUnparseable() {
        RegistryService service = newService("main.registry", URI.create("https"), "%s",
                DockerAuthScheme.BASIC, "user", "pass", null);

        assertThat(service.dockerConfigForImages(List.of("INVALID IMAGE!!"))).isEmpty();
    }
}