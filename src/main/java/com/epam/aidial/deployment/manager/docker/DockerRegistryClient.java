package com.epam.aidial.deployment.manager.docker;

import com.epam.aidial.deployment.manager.configuration.DockerAuthScheme;
import com.epam.aidial.deployment.manager.configuration.RegistryProperties;
import com.epam.aidial.deployment.manager.configuration.logging.LogExecution;
import com.epam.aidial.deployment.manager.docker.dto.ContainerConfigurationTemplateDto;
import com.epam.aidial.deployment.manager.model.ImageEntrypoint;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.cloud.tools.jib.api.Credential;
import com.google.cloud.tools.jib.api.DescriptorDigest;
import com.google.cloud.tools.jib.api.ImageReference;
import com.google.cloud.tools.jib.api.LogEvent;
import com.google.cloud.tools.jib.blob.Blobs;
import com.google.cloud.tools.jib.event.EventHandlers;
import com.google.cloud.tools.jib.http.FailoverHttpClient;
import com.google.cloud.tools.jib.image.json.BuildableManifestTemplate;
import com.google.cloud.tools.jib.image.json.ManifestTemplate;
import com.google.cloud.tools.jib.image.json.OciIndexTemplate;
import com.google.cloud.tools.jib.image.json.OciManifestTemplate;
import com.google.cloud.tools.jib.image.json.V22ManifestTemplate;
import com.google.cloud.tools.jib.registry.RegistryClient;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.ListUtils;
import org.slf4j.event.Level;
import org.springframework.stereotype.Service;

import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Base64;
import java.util.Objects;

@Slf4j
@Service
@LogExecution
@RequiredArgsConstructor
public class DockerRegistryClient {

    private static final String MANIFEST_API_URL_TEMPLATE = "%s://%s/v2/%s/manifests/%s";

    private final RegistryProperties registryProperties;
    private final ObjectMapper objectMapper;
    private final HttpConnectionFactory httpConnectionFactory;

    @SneakyThrows
    public ImageEntrypoint getEntrypoint(String imageName) {
        var registryClient = getRegistryClient(imageName);

        var imageReference = ImageReference.parse(imageName);
        var imageQualifier = imageReference.getTag().orElse("latest");
        var manifest = getManifest(imageQualifier, registryClient);
        var configDigest = retrieveConfigDigest(imageName, manifest, registryClient);

        log.debug("Pulling Image Configuration Blob. Image name: {}.", imageName);
        var configBlob = registryClient.pullBlob(
                configDigest,
                size -> {
                }, // No-op progress, rely on global handler
                written -> {
                } // No-op progress, rely on global handler
        );
        log.debug("Configuration blob pulled successfully. Image name: {}.", imageName);

        var configTemplate = objectMapper.readValue(Blobs.writeToByteArray(configBlob), ContainerConfigurationTemplateDto.class);

        return new ImageEntrypoint(
                ListUtils.emptyIfNull(configTemplate.getConfig().getEntrypoint()),
                ListUtils.emptyIfNull(configTemplate.getConfig().getCmd())
        );
    }

    @SneakyThrows
    private ManifestTemplate getManifest(String imageQualifier, RegistryClient registryClient) {
        var manifestAndDigest = registryClient.pullManifest(imageQualifier);
        return manifestAndDigest.getManifest();
    }

    @SneakyThrows
    private DescriptorDigest retrieveConfigDigest(String imageName, ManifestTemplate manifest, RegistryClient registryClient) {
        log.debug("Retrieving config digest. Image name: {}.", imageName);
        BuildableManifestTemplate.ContentDescriptorTemplate configDescriptor;
        if (manifest instanceof V22ManifestTemplate v22ManifestTemplate) {
            configDescriptor = v22ManifestTemplate.getContainerConfiguration();
        } else if (manifest instanceof OciManifestTemplate ociManifestTemplate) {
            configDescriptor = ociManifestTemplate.getContainerConfiguration();
        } else if (manifest instanceof OciIndexTemplate ociIndexTemplate) {
            var manifestDescriptorTemplate = ociIndexTemplate.getManifests().get(0);
            var digest = manifestDescriptorTemplate.getDigest();
            if (digest == null) {
                throw new IllegalStateException("Cannot retrieve config digest. Manifest does not contain a digest. "
                        + "Image name: %s".formatted(imageName));
            }
            var specificManifest = getManifest(digest.toString(), registryClient);
            return retrieveConfigDigest(imageName, specificManifest, registryClient);
        } else {
            throw new IllegalStateException("Manifest is not a recognized type that contains a container configuration. "
                    + "Image name: %s".formatted(imageName));
        }

        if (configDescriptor == null) {
            throw new IllegalStateException("Manifest does not contain a reference to a container configuration blob. "
                    + "Image name: %s".formatted(imageName));
        }

        var configDigest = configDescriptor.getDigest();
        log.debug("Config digest retrieved: {}. Image name: {}.", configDigest, imageName);
        return configDigest;
    }

    @SneakyThrows
    private RegistryClient getRegistryClient(String imageName) {
        var imageReference = ImageReference.parse(imageName);

        var eventHandlers = EventHandlers.builder()
                .add(LogEvent.class, logEvent ->
                        log.atLevel(toSlf4jLogLevel(logEvent.getLevel())).log(logEvent.getMessage()))
                .build();

        var registryClientFactory = RegistryClient.factory(
                eventHandlers,
                imageReference.getRegistry(),
                imageReference.getRepository(),
                new FailoverHttpClient(true, true, eventHandlers::dispatch));

        // Fall back to main registry auth
        if (registryProperties.getAuth() == DockerAuthScheme.BASIC
                && Objects.equals(registryProperties.getUrl(), imageReference.getRegistry())) {
            var credential = Credential.from(registryProperties.getUser(), registryProperties.getPassword());
            registryClientFactory.setCredential(credential);
            var registryClient = registryClientFactory.newRegistryClient();
            registryClient.configureBasicAuth();
            return registryClient;
        }

        // Check if registry is in trusted registries
        var trustedRegistries = registryProperties.getTrustedPrivateRegistries();
        for (var trustedRegistry : trustedRegistries) {
            if (Objects.equals(trustedRegistry.getRegistry(), imageReference.getRegistry())
                    && "BASIC".equals(trustedRegistry.getAuthScheme())
                    && trustedRegistry.getUser() != null
                    && trustedRegistry.getPassword() != null) {
                var credential = Credential.from(trustedRegistry.getUser(), trustedRegistry.getPassword());
                registryClientFactory.setCredential(credential);
                var registryClient = registryClientFactory.newRegistryClient();
                registryClient.configureBasicAuth();
                return registryClient;
            }
        }

        var registryClient = registryClientFactory.newRegistryClient();
        registryClient.doPullBearerAuth();
        return registryClient;

    }

    public void deleteImage(String imageName) throws Exception {
        var imageReference = ImageReference.parse(imageName);
        var imageQualifier = imageReference.getTag().orElse("latest");
        var digest = getManifestDigest(imageReference.getRepository(), imageQualifier);

        if (digest != null) {
            deleteManifestByDigest(imageReference.getRepository(), digest);
            log.info("Image deleted successfully: {}", imageName);
        }
    }

    private String getManifestDigest(String repository, String imageTag) throws Exception {
        var connection = configureHttpConnToManifestApi("GET", repository, imageTag);
        connection.setRequestProperty(
                "Accept",
                "application/vnd.docker.distribution.manifest.v2+json, application/vnd.oci.image.manifest.v1+json"
        );

        try {
            connection.connect();

            int responseCode = connection.getResponseCode();
            String responseMessage = connection.getResponseMessage();
            if (responseCode == 200) {
                return connection.getHeaderField("Docker-Content-Digest");
            } else if (responseCode == 404) {
                log.error("Manifest is not found. Repository: {}. Image Tag: {}", repository, imageTag);
                return null;
            } else {
                throw new RuntimeException("Failed to retrieve manifest digest. HTTP response code: %s. Message: %s"
                        .formatted(responseCode, responseMessage));
            }

        } finally {
            connection.disconnect();
        }
    }

    private void deleteManifestByDigest(String repository, String digest) throws Exception {
        var connection = configureHttpConnToManifestApi("DELETE", repository, digest);
        try {
            connection.connect();

            int responseCode = connection.getResponseCode();
            if (responseCode != 202) {
                throw new RuntimeException("Failed to delete manifest. HTTP response code: " + responseCode);
            }

        } finally {
            connection.disconnect();
        }
    }

    private HttpURLConnection configureHttpConnToManifestApi(String requestMethod, String repository, String lastParam) throws Exception {
        String formattedUrl = String.format(MANIFEST_API_URL_TEMPLATE,
                registryProperties.getProtocol(), registryProperties.getUrl(), repository, lastParam);

        URL url = new URL(formattedUrl);
        HttpURLConnection connection = httpConnectionFactory.createConnection(url);
        connection.setRequestMethod(requestMethod);

        if (registryProperties.getAuth() == DockerAuthScheme.BASIC) {
            String auth = registryProperties.getUser() + ":" + registryProperties.getPassword();
            String encodedAuth = Base64.getEncoder().encodeToString(auth.getBytes());
            connection.setRequestProperty("Authorization", "Basic " + encodedAuth);
        }

        return connection;
    }

    private Level toSlf4jLogLevel(LogEvent.Level level) {
        return switch (level) {
            case ERROR -> Level.ERROR;
            case WARN -> Level.WARN;
            case LIFECYCLE, INFO, PROGRESS -> Level.INFO;
            case DEBUG -> Level.DEBUG;
        };
    }

}
