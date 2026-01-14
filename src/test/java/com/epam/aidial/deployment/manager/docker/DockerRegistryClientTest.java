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
import org.springframework.test.util.ReflectionTestUtils;

import java.io.ByteArrayInputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
            assertNotNull(result);
            assertEquals(List.of("sh"), result.getEntrypoint());
            assertEquals(List.of("-c", "echo hello"), result.getCmd());

            // When BASIC auth and registry matches, credentials should be set, not bearer auth
            var credentialCaptor = ArgumentCaptor.forClass(com.google.cloud.tools.jib.api.Credential.class);
            verify(registryClientFactory).setCredential(credentialCaptor.capture());
            var credential = credentialCaptor.getValue();
            assertEquals("testuser", credential.getUsername());
            assertEquals("testpass", credential.getPassword());
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
            assertEquals("testuser", credential.getUsername());
            assertEquals("testpass", credential.getPassword());
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
        var exception = assertThrows(RuntimeException.class, () -> {
            ReflectionTestUtils.invokeMethod(dockerRegistryClient, "getManifestDigest", repository, imageTag);
        });

        assertEquals("Failed to retrieve manifest digest. HTTP response code: 500. Message: Something happened", exception.getMessage());
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
        var exception = assertThrows(RuntimeException.class, () -> {
            ReflectionTestUtils.invokeMethod(dockerRegistryClient, "deleteManifestByDigest", repository, digest);
        });

        assertEquals("Failed to delete manifest. HTTP response code: 500", exception.getMessage());
    }

    @Test
    void toSlf4jLogLevel_shouldMapLogLevelsCorrectly() {
        // Given/When/Then
        assertEquals(org.slf4j.event.Level.ERROR, ReflectionTestUtils.invokeMethod(dockerRegistryClient, "toSlf4jLogLevel", LogEvent.Level.ERROR));
        assertEquals(org.slf4j.event.Level.WARN, ReflectionTestUtils.invokeMethod(dockerRegistryClient, "toSlf4jLogLevel", LogEvent.Level.WARN));
        assertEquals(org.slf4j.event.Level.INFO, ReflectionTestUtils.invokeMethod(dockerRegistryClient, "toSlf4jLogLevel", LogEvent.Level.INFO));
        assertEquals(org.slf4j.event.Level.INFO, ReflectionTestUtils.invokeMethod(dockerRegistryClient, "toSlf4jLogLevel", LogEvent.Level.LIFECYCLE));
        assertEquals(org.slf4j.event.Level.INFO, ReflectionTestUtils.invokeMethod(dockerRegistryClient, "toSlf4jLogLevel", LogEvent.Level.PROGRESS));
        assertEquals(org.slf4j.event.Level.DEBUG, ReflectionTestUtils.invokeMethod(dockerRegistryClient, "toSlf4jLogLevel", LogEvent.Level.DEBUG));
    }
}