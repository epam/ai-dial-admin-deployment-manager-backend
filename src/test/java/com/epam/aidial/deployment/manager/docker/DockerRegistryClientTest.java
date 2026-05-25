package com.epam.aidial.deployment.manager.docker;

import com.epam.aidial.deployment.manager.configuration.DockerAuthScheme;
import com.epam.aidial.deployment.manager.configuration.RegistryProperties;
import com.epam.aidial.deployment.manager.docker.dto.ContainerConfigurationTemplateDto;
import com.epam.aidial.deployment.manager.model.ImageEntrypoint;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.cloud.tools.jib.api.DescriptorDigest;
import com.google.cloud.tools.jib.api.ImageReference;
import com.google.cloud.tools.jib.api.LogEvent;
import com.google.cloud.tools.jib.blob.Blobs;
import com.google.cloud.tools.jib.event.EventHandlers;
import com.google.cloud.tools.jib.http.FailoverHttpClient;
import com.google.cloud.tools.jib.image.json.ManifestTemplate;
import com.google.cloud.tools.jib.image.json.V22ManifestTemplate;
import com.google.cloud.tools.jib.registry.ManifestAndDigest;
import com.google.cloud.tools.jib.registry.RegistryClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.event.Level;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.ByteArrayInputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DockerRegistryClientTest {

    private DockerRegistryClient dockerRegistryClient;

    @Mock
    private ObjectMapper objectMapper;
    @Mock
    private RegistryClient registryClient;
    @Mock
    private HttpConnectionFactory httpConnectionFactory;
    @Mock
    private RegistryClient.Factory registryClientFactory;

    @BeforeEach
    void setUp() {
        RegistryProperties registryProperties = new RegistryProperties();
        registryProperties.setAuth(DockerAuthScheme.BASIC);
        registryProperties.setUrl("registry.example.com");
        registryProperties.setProtocol(URI.create("https"));
        registryProperties.setUser("testuser");
        registryProperties.setPassword("testpass");
        
        dockerRegistryClient = new DockerRegistryClient(registryProperties, objectMapper, httpConnectionFactory);
    }

    @Test
    void getEntrypoint_shouldReturnImageEntrypoint() throws Exception {
        // Given
        String imageName = "registry.example.com/repo/image:tag";

        try (MockedStatic<ImageReference> imageReferenceMockedStatic = mockStatic(ImageReference.class);
                MockedStatic<RegistryClient> registryClientMockedStatic = mockStatic(RegistryClient.class)) {

            var imageReference = mock(ImageReference.class);
            imageReferenceMockedStatic.when(() -> ImageReference.parse(imageName)).thenReturn(imageReference);
            when(imageReference.getRegistry()).thenReturn("registry.example.com");
            when(imageReference.getRepository()).thenReturn("repo/image");
            when(imageReference.getTag()).thenReturn(java.util.Optional.of("tag"));

            // Mock RegistryClient
            registryClientMockedStatic.when(() -> RegistryClient.factory(any(EventHandlers.class), anyString(), anyString(), any(FailoverHttpClient.class)))
                    .thenReturn(registryClientFactory);
            when(registryClientFactory.newRegistryClient()).thenReturn(registryClient);

            // Mock manifest
            var manifestTemplate = mock(V22ManifestTemplate.class);
            V22ManifestTemplate.ContentDescriptorTemplate configDescriptor = mock(V22ManifestTemplate.ContentDescriptorTemplate.class);
            ManifestAndDigest<ManifestTemplate> manifestAndDigest = mock(ManifestAndDigest.class);

            when(registryClient.pullManifest("tag")).thenReturn(manifestAndDigest);
            when(manifestAndDigest.getManifest()).thenReturn(manifestTemplate);
            when(manifestTemplate.getContainerConfiguration()).thenReturn(configDescriptor);

            // Mock config digest
            var descriptorDigest = mock(DescriptorDigest.class);
            when(configDescriptor.getDigest()).thenReturn(descriptorDigest);

            // Mock blob
            var blob = Blobs.from(new ByteArrayInputStream("{\"config\":{\"Entrypoint\":[\"sh\"],\"Cmd\":[\"-c\",\"echo hello\"]}}".getBytes()));
            when(registryClient.pullBlob(eq(descriptorDigest), any(), any())).thenReturn(blob);

            // Mock object mapper
            var configTemplate = new ContainerConfigurationTemplateDto();
            configTemplate.getConfig().setEntrypoint(List.of("sh"));
            configTemplate.getConfig().setCmd(List.of("-c", "echo hello"));
            when(objectMapper.readValue(any(byte[].class), eq(ContainerConfigurationTemplateDto.class))).thenReturn(configTemplate);

            // When
            ImageEntrypoint result = dockerRegistryClient.getEntrypoint(imageName);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getEntrypoint()).containsExactly("sh");
            assertThat(result.getCmd()).containsExactly("-c", "echo hello");

            // When BASIC auth and registry matches, credentials should be set, not bearer auth
            var credentialCaptor = ArgumentCaptor.forClass(com.google.cloud.tools.jib.api.Credential.class);
            verify(registryClientFactory).setCredential(credentialCaptor.capture());
            var credential = credentialCaptor.getValue();
            assertThat(credential.getUsername()).isEqualTo("testuser");
            assertThat(credential.getPassword()).isEqualTo("testpass");
        }
    }

    @Test
    void getRegistryClient_shouldSetCredentialsWhenAuthSchemeIsBasicAndRegistryMatches() {
        try (MockedStatic<ImageReference> imageReferenceMockedStatic = mockStatic(ImageReference.class);
                MockedStatic<RegistryClient> registryClientMockedStatic = mockStatic(RegistryClient.class)) {
            
            var imageReference = mock(ImageReference.class);
            imageReferenceMockedStatic.when(() -> ImageReference.parse("registry.example.com/repo/image:tag"))
                    .thenReturn(imageReference);
            when(imageReference.getRegistry()).thenReturn("registry.example.com");
            when(imageReference.getRepository()).thenReturn("repo/image");
            
            registryClientMockedStatic.when(() -> RegistryClient.factory(any(EventHandlers.class), anyString(), anyString(), any(FailoverHttpClient.class)))
                    .thenReturn(registryClientFactory);
            when(registryClientFactory.newRegistryClient()).thenReturn(registryClient);
            
            // When
            ReflectionTestUtils.invokeMethod(dockerRegistryClient, "getRegistryClient", "registry.example.com/repo/image:tag");
            
            // Then - credentials should be set, not bearer auth
            var credentialCaptor = ArgumentCaptor.forClass(com.google.cloud.tools.jib.api.Credential.class);
            verify(registryClientFactory).setCredential(credentialCaptor.capture());
            
            var credential = credentialCaptor.getValue();
            assertThat(credential.getUsername()).isEqualTo("testuser");
            assertThat(credential.getPassword()).isEqualTo("testpass");
        }
    }

    @Test
    void getRegistryClient_shouldNotSetCredentialsWhenRegistryDoesNotMatch() throws Exception {
        try (MockedStatic<ImageReference> imageReferenceMockedStatic = mockStatic(ImageReference.class);
                MockedStatic<RegistryClient> registryClientMockedStatic = mockStatic(RegistryClient.class)) {
            
            var imageReference = mock(ImageReference.class);
            imageReferenceMockedStatic.when(() -> ImageReference.parse("other-registry.example.com/repo/image:tag"))
                    .thenReturn(imageReference);
            when(imageReference.getRegistry()).thenReturn("other-registry.example.com");
            when(imageReference.getRepository()).thenReturn("repo/image");
            
            registryClientMockedStatic.when(() -> RegistryClient.factory(any(EventHandlers.class), anyString(), anyString(), any(FailoverHttpClient.class)))
                    .thenReturn(registryClientFactory);
            when(registryClientFactory.newRegistryClient()).thenReturn(registryClient);
            
            // When
            ReflectionTestUtils.invokeMethod(dockerRegistryClient, "getRegistryClient", "other-registry.example.com/repo/image:tag");
            
            // Then - credentials should NOT be set, should use bearer auth
            verify(registryClientFactory, times(0)).setCredential(any());
            verify(registryClient).doPullBearerAuth();
        }
    }

    @Test
    void getRegistryClient_shouldUseBearerAuthAndCredentialsForDockerHubTrustedRegistry() throws Exception {
        // Reproduces the failing scenario: trusted entry says "docker.io", image reference resolves
        // (via jib) to "registry-1.docker.io". Must match -> set credentials -> use bearer flow (not
        // configureBasicAuth, which Docker Hub rejects with 401).
        RegistryProperties registryProperties = new RegistryProperties();
        registryProperties.setAuth(DockerAuthScheme.NONE);
        registryProperties.setUrl("registry.example.com");
        registryProperties.setProtocol(URI.create("https"));

        RegistryProperties.TrustedPrivateRegistry hubEntry = new RegistryProperties.TrustedPrivateRegistry();
        hubEntry.setRegistry("docker.io");
        hubEntry.setAuthScheme("BASIC");
        hubEntry.setUser("hubuser");
        hubEntry.setPassword("hubtoken");
        registryProperties.setTrustedPrivateRegistries(List.of(hubEntry));

        DockerRegistryClient client = new DockerRegistryClient(registryProperties, objectMapper, httpConnectionFactory);

        try (MockedStatic<ImageReference> imageReferenceMockedStatic = mockStatic(ImageReference.class);
                MockedStatic<RegistryClient> registryClientMockedStatic = mockStatic(RegistryClient.class)) {

            var imageReference = mock(ImageReference.class);
            imageReferenceMockedStatic.when(() -> ImageReference.parse("d0nets/private-simple-mcp"))
                    .thenReturn(imageReference);
            when(imageReference.getRegistry()).thenReturn("registry-1.docker.io");
            when(imageReference.getRepository()).thenReturn("d0nets/private-simple-mcp");

            registryClientMockedStatic.when(() -> RegistryClient.factory(any(EventHandlers.class), anyString(), anyString(), any(FailoverHttpClient.class)))
                    .thenReturn(registryClientFactory);
            when(registryClientFactory.newRegistryClient()).thenReturn(registryClient);

            ReflectionTestUtils.invokeMethod(client, "getRegistryClient", "d0nets/private-simple-mcp");

            var credentialCaptor = ArgumentCaptor.forClass(com.google.cloud.tools.jib.api.Credential.class);
            verify(registryClientFactory).setCredential(credentialCaptor.capture());
            assertThat(credentialCaptor.getValue().getUsername()).isEqualTo("hubuser");
            assertThat(credentialCaptor.getValue().getPassword()).isEqualTo("hubtoken");

            // Critical: bearer flow (Docker Hub rejects plain Basic on the registry API).
            verify(registryClient).doPullBearerAuth();
        }
    }

    @Test
    void isSameRegistry_shouldTreatDockerHubAliasesAsSame() {
        // jib-core canonicalizes Docker Hub image references to "registry-1.docker.io".
        // A user-configured trusted entry naming "docker.io" must match that canonical form.
        assertThat(DockerRegistryClient.isSameRegistry("docker.io", "registry-1.docker.io")).isTrue();
        assertThat(DockerRegistryClient.isSameRegistry("registry-1.docker.io", "docker.io")).isTrue();
        assertThat(DockerRegistryClient.isSameRegistry("index.docker.io", "registry-1.docker.io")).isTrue();
        assertThat(DockerRegistryClient.isSameRegistry("docker.io", "index.docker.io")).isTrue();
    }

    @Test
    void isSameRegistry_shouldFallBackToEqualityForNonHubRegistries() {
        assertThat(DockerRegistryClient.isSameRegistry("registry.example.com", "registry.example.com")).isTrue();
        assertThat(DockerRegistryClient.isSameRegistry("my.private.registry:5000", "my.private.registry:5000")).isTrue();
    }

    @Test
    void isSameRegistry_shouldReturnFalseForUnrelatedRegistries() {
        // Non-Hub regression guard: unrelated registries must never collapse.
        assertThat(DockerRegistryClient.isSameRegistry("a.example.com", "b.example.com")).isFalse();
        // One side a Hub alias, the other a private host — still distinct.
        assertThat(DockerRegistryClient.isSameRegistry("docker.io", "registry.example.com")).isFalse();
        assertThat(DockerRegistryClient.isSameRegistry("registry.example.com", "registry-1.docker.io")).isFalse();
    }

    @Test
    void deleteImage_shouldDeleteManifestWhenImageExists() throws Exception {
        // Given
        String imageName = "registry.example.com/repo/image:tag";
        String digest = "sha256:1234567890abcdef";

        try (MockedStatic<ImageReference> imageReferenceMockedStatic = mockStatic(ImageReference.class)) {
            var imageReference = mock(ImageReference.class);
            imageReferenceMockedStatic.when(() -> ImageReference.parse(imageName)).thenReturn(imageReference);
            when(imageReference.getRepository()).thenReturn("repo/image");
            when(imageReference.getTag()).thenReturn(java.util.Optional.of("tag"));

            var httpConnection = mock(HttpURLConnection.class);
            when(httpConnection.getResponseCode()).thenReturn(200);
            when(httpConnection.getHeaderField("Docker-Content-Digest")).thenReturn(digest);

            var deleteConnection = mock(HttpURLConnection.class);
            when(deleteConnection.getResponseCode()).thenReturn(202);
            when(httpConnectionFactory.createConnection(any()))
                    .thenReturn(httpConnection)
                    .thenReturn(deleteConnection);

            // When
            dockerRegistryClient.deleteImage(imageName);

            // Then
            verify(httpConnection).setRequestProperty("Accept", "application/vnd.docker.distribution.manifest.v2+json, application/vnd.oci.image.manifest.v1+json");
            verify(httpConnection).setRequestProperty("Authorization", "Basic dGVzdHVzZXI6dGVzdHBhc3M=");
            verify(deleteConnection).setRequestProperty("Authorization", "Basic dGVzdHVzZXI6dGVzdHBhc3M=");
            verify(deleteConnection).connect();
        }
    }

    @Test
    void deleteImage_shouldHandleNonExistentImage() throws Exception {
        // Given
        String imageName = "registry.example.com/repo/image:tag";

        try (MockedStatic<ImageReference> imageReferenceMockedStatic = mockStatic(ImageReference.class)) {
            var imageReference = mock(ImageReference.class);
            imageReferenceMockedStatic.when(() -> ImageReference.parse(imageName)).thenReturn(imageReference);
            when(imageReference.getRepository()).thenReturn("repo/image");
            when(imageReference.getTag()).thenReturn(java.util.Optional.of("tag"));

            var httpConnection = mock(HttpURLConnection.class);
            when(httpConnectionFactory.createConnection(any())).thenReturn(httpConnection);
            when(httpConnection.getResponseCode()).thenReturn(404);

            // When
            dockerRegistryClient.deleteImage(imageName);

            // Then
            verify(httpConnection).setRequestProperty("Accept", "application/vnd.docker.distribution.manifest.v2+json, application/vnd.oci.image.manifest.v1+json");
            verify(httpConnection).setRequestProperty("Authorization", "Basic dGVzdHVzZXI6dGVzdHBhc3M=");
        }
    }

    @Test
    void getManifestDigest_shouldThrowExceptionOnUnexpectedResponseCode() throws Exception {
        // Given
        String repository = "repo/image";
        String imageTag = "tag";

        var httpConnection = mock(HttpURLConnection.class);
        when(httpConnectionFactory.createConnection(any())).thenReturn(httpConnection);
        when(httpConnection.getResponseCode()).thenReturn(500);
        when(httpConnection.getResponseMessage()).thenReturn("Something happened");

        // When/Then
        assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(dockerRegistryClient, "getManifestDigest", repository, imageTag))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Failed to retrieve manifest digest. HTTP response code: 500. Message: Something happened");
    }

    @Test
    void deleteManifestByDigest_shouldThrowExceptionOnUnexpectedResponseCode() throws Exception {
        // Given
        String repository = "repo/image";
        String digest = "sha256:1234567890abcdef";

        var httpConnection = mock(HttpURLConnection.class);
        when(httpConnectionFactory.createConnection(any())).thenReturn(httpConnection);
        when(httpConnection.getResponseCode()).thenReturn(500);

        // When/Then
        assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(dockerRegistryClient, "deleteManifestByDigest", repository, digest))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Failed to delete manifest. HTTP response code: 500");
    }

    @Test
    void toSlf4jLogLevel_shouldMapLogLevelsCorrectly() {
        // Given/When/Then
        assertThat((Level) ReflectionTestUtils.invokeMethod(dockerRegistryClient, "toSlf4jLogLevel", LogEvent.Level.ERROR)).isEqualTo(Level.ERROR);
        assertThat((Level) ReflectionTestUtils.invokeMethod(dockerRegistryClient, "toSlf4jLogLevel", LogEvent.Level.WARN)).isEqualTo(Level.WARN);
        assertThat((Level) ReflectionTestUtils.invokeMethod(dockerRegistryClient, "toSlf4jLogLevel", LogEvent.Level.INFO)).isEqualTo(Level.INFO);
        assertThat((Level) ReflectionTestUtils.invokeMethod(dockerRegistryClient, "toSlf4jLogLevel", LogEvent.Level.LIFECYCLE)).isEqualTo(Level.INFO);
        assertThat((Level) ReflectionTestUtils.invokeMethod(dockerRegistryClient, "toSlf4jLogLevel", LogEvent.Level.PROGRESS)).isEqualTo(Level.INFO);
        assertThat((Level) ReflectionTestUtils.invokeMethod(dockerRegistryClient, "toSlf4jLogLevel", LogEvent.Level.DEBUG)).isEqualTo(Level.DEBUG);
    }
}