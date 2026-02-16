package com.epam.aidial.deployment.manager.functional.utils;

import com.epam.aidial.deployment.manager.model.AdapterImageDefinition;
import com.epam.aidial.deployment.manager.model.DeploymentMetadata;
import com.epam.aidial.deployment.manager.model.DockerImageSource;
import com.epam.aidial.deployment.manager.model.EnvVar;
import com.epam.aidial.deployment.manager.model.EnvVarDefinition;
import com.epam.aidial.deployment.manager.model.EnvVarMountType;
import com.epam.aidial.deployment.manager.model.FileEnvVarValue;
import com.epam.aidial.deployment.manager.model.GitDockerfileImageSource;
import com.epam.aidial.deployment.manager.model.ImageBuilder;
import com.epam.aidial.deployment.manager.model.ImageDefinition;
import com.epam.aidial.deployment.manager.model.InterceptorImageDefinition;
import com.epam.aidial.deployment.manager.model.McpImageDefinition;
import com.epam.aidial.deployment.manager.model.McpTransport;
import com.epam.aidial.deployment.manager.model.McpTransportType;
import com.epam.aidial.deployment.manager.model.Resources;
import com.epam.aidial.deployment.manager.model.SensitiveEnvVar;
import com.epam.aidial.deployment.manager.model.SensitiveFileEnvVar;
import com.epam.aidial.deployment.manager.model.SimpleEnvVar;
import com.epam.aidial.deployment.manager.model.SimpleEnvVarValue;
import com.epam.aidial.deployment.manager.model.deployment.CreateDeployment;
import com.epam.aidial.deployment.manager.model.deployment.CreateInterceptorDeployment;
import com.epam.aidial.deployment.manager.model.deployment.CreateMcpDeployment;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class FunctionalTestHelper {

    public static McpImageDefinition createMcpImageDefinition() {
        var source = new DockerImageSource("http://test-uri", List.of("entry1"));
        return McpImageDefinition.builder()
                .version("1.0.0")
                .name("mcpImage")
                .description("someDesc")
                .license("someLicense")
                .source(source)
                .transportType(McpTransportType.REMOTE)
                .topics(getTopics())
                .author("test-author")
                .allowedDomains(List.of())
                .imageBuilder(ImageBuilder.BUILDKIT_ROOTLESS)
                .build();
    }

    public static ImageDefinition createInterceptorImageDefinition() {
        var source = new DockerImageSource("http://test-uri-1", List.of("entry2"));
        return InterceptorImageDefinition.builder()
                .name("interceptorImage")
                .version("1.0.0")
                .description("someDesc")
                .license("someLicense")
                .source(source)
                .topics(getTopics())
                .author("test-author")
                .allowedDomains(List.of())
                .imageBuilder(ImageBuilder.BUILDKIT)
                .build();
    }

    public static ImageDefinition createRealInterceptorImageDefinition(String imageUri) {
        var source = new DockerImageSource(imageUri, List.of());
        return InterceptorImageDefinition.builder()
                .name("interceptor-example-test")
                .version("1.0.0")
                .description("End-to-end testing")
                .license("")
                .source(source)
                .topics(getTopics())
                .allowedDomains(new ArrayList<>())
                .imageBuilder(ImageBuilder.BUILDKIT_ROOTLESS)
                .build();
    }

    public static ImageDefinition createRealMcpDockerStdioImageDefinition(String imageUri) {
        // Image is not MCP, but it doesn't matter for tests
        var source = new DockerImageSource(imageUri,
                List.of("/docker_entrypoint.sh", "/bin/sh", "-c", "uvicorn aidial_interceptors_sdk.examples.app:app --host 0.0.0.0 --port 8080"));

        return McpImageDefinition.builder()
                .name("mcp-docker-stdio-test")
                .version("1.0.0")
                .description("End-to-end testing")
                .license("")
                .source(source)
                .topics(getTopics())
                .transportType(McpTransportType.LOCAL)
                .allowedDomains(new ArrayList<>())
                .imageBuilder(ImageBuilder.BUILDKIT_ROOTLESS)
                .build();
    }

    public static ImageDefinition createRealMcpGitSseImageDefinition(String imageUrl) {
        var source = GitDockerfileImageSource.builder()
                .url(imageUrl)
                .build();
        return McpImageDefinition.builder()
                .name("mcp-git-sse-test")
                .version("1.0.0")
                .description("End-to-end testing")
                .license("")
                .source(source)
                .topics(getTopics())
                .transportType(McpTransportType.REMOTE)
                .allowedDomains(new ArrayList<>())
                .imageBuilder(ImageBuilder.BUILDKIT_ROOTLESS)
                .build();
    }

    public static ImageDefinition createRealMcpGitStdioImageDefinition(String imageUrl) {
        var source = GitDockerfileImageSource.builder()
                .url(imageUrl)
                .build();
        return McpImageDefinition.builder()
                .name("mcp-git-stdio-test")
                .version("1.0.0")
                .description("End-to-end testing")
                .license("")
                .source(source)
                .topics(getTopics())
                .transportType(McpTransportType.LOCAL)
                .allowedDomains(new ArrayList<>())
                .imageBuilder(ImageBuilder.BUILDKIT_ROOTLESS)
                .build();
    }

    public static ImageDefinition createAdapterImageDefinition() {
        var source = new DockerImageSource("http://test-uri-adapter", List.of("entry-adapter"));
        return AdapterImageDefinition.builder()
                .name("adapterImage")
                .version("1.0.0")
                .description("adapterDesc")
                .license("adapterLicense")
                .source(source)
                .topics(getTopics())
                .author("test-author")
                .allowedDomains(List.of())
                .imageBuilder(ImageBuilder.BUILDKIT_ROOTLESS)
                .build();
    }

    public static GitDockerfileImageSource createGitImageSource() {
        return GitDockerfileImageSource.builder()
                .url("https://github.com/test/test-repo.git")
                .branchName("main")
                .sha("abc123def456")
                .baseDirectory("src")
                .entrypoint(List.of("python", "app.py"))
                .build();
    }

    public static CreateDeployment createInterceptorDeploymentRequest(UUID imageDefinitionId) {
        return CreateInterceptorDeployment.builder()
                .id("interceptor-test-deployment-1")
                .imageDefinitionId(imageDefinitionId)
                .displayName("test-deployment")
                .description("Test deployment description")
                .initialScale(1)
                .minScale(0)
                .maxScale(5)
                .resources(createResources())
                .author("test-author")
                .metadata(createMetadata())
                .allowedDomains(List.of())
                .build();
    }

    public static CreateDeployment createRealInterceptorDeploymentRequest(String name, List<EnvVarDefinition> envs) {
        return CreateInterceptorDeployment.builder()
                .id(name)
                .displayName(name)
                .description("Test deployment description")
                .initialScale(1)
                .minScale(0)
                .maxScale(5)
                .resources(createResources())
                .author("test-author")
                .metadata(new DeploymentMetadata(envs))
                .allowedDomains(List.of("github.com", "epam.com"))
                .build();
    }

    public static CreateDeployment createMcpDeploymentRequest(UUID imageDefinitionId) {
        return CreateMcpDeployment.builder()
                .id("mcp-test-deployment-1")
                .imageDefinitionId(imageDefinitionId)
                .displayName("test-deployment")
                .description("Test deployment description")
                .initialScale(1)
                .minScale(0)
                .maxScale(5)
                .resources(createResources())
                .author("test-author")
                .metadata(createMetadata())
                .transport(McpTransport.SSE)
                .mcpEndpointPath("some-path")
                .allowedDomains(List.of())
                .build();
    }

    public static CreateDeployment createRealMcpDeploymentRequest(String name, List<EnvVarDefinition> envs) {
        return CreateMcpDeployment.builder()
                .id(name)
                .displayName(name)
                .description("Test deployment description")
                .initialScale(1)
                .minScale(0)
                .maxScale(5)
                .resources(createResources())
                .author("test-author")
                .metadata(new DeploymentMetadata(envs))
                .transport(McpTransport.SSE)
                .mcpEndpointPath("some-path")
                .allowedDomains(List.of())
                .build();
    }

    public static List<EnvVar> getEnvVarsWithoutK8sSecretName() {
        EnvVar env1 = new SimpleEnvVar("env1", new SimpleEnvVarValue("test-value-1"));
        EnvVar env2 = new SensitiveEnvVar("env2", new SimpleEnvVarValue("val2"), null, "env2");
        EnvVar env4 = new SimpleEnvVar("env4", new FileEnvVarValue("file4.json", "some-file-content-4"));
        EnvVar env5 = new SensitiveEnvVar("env5", new FileEnvVarValue("file5.json", "file-content-5"), null, "env5");
        EnvVar env3 = SensitiveFileEnvVar.builder()
                .name("env3")
                .value(new SimpleEnvVarValue("ewogIHRva2VuOiAxMjMKfQ=="))
                .k8sSecretName(null)
                .k8sSecretKey("env3")
                .build();
        EnvVar env6 = SensitiveFileEnvVar.builder()
                .name("env6")
                .value(new FileEnvVarValue("file6.json", "ewogIHRva2VuOiAxMjMKfQ=="))
                .k8sSecretName(null)
                .k8sSecretKey("env6")
                .build();
        return List.of(env1, env2, env3, env4, env5, env6);
    }

    public static List<EnvVar> getEnvVarsWithoutK8sSecretNameAndSecrets() {
        EnvVar env1 = new SimpleEnvVar("env1", new SimpleEnvVarValue("test-value-1"));
        EnvVar env2 = new SensitiveEnvVar("env2", new SimpleEnvVarValue(null), null, "env2");
        EnvVar env4 = new SimpleEnvVar("env4", new FileEnvVarValue("file4.json", "some-file-content-4"));
        EnvVar env5 = new SensitiveEnvVar("env5", new FileEnvVarValue("file5.json", null), null, "env5");
        EnvVar env3 = SensitiveFileEnvVar.builder()
                .name("env3")
                .value(new SimpleEnvVarValue(null))
                .k8sSecretName(null)
                .k8sSecretKey("env3")
                .build();
        EnvVar env6 = SensitiveFileEnvVar.builder()
                .name("env6")
                .value(new FileEnvVarValue("file6.json", null))
                .k8sSecretName(null)
                .k8sSecretKey("env6")
                .build();
        return List.of(env1, env2, env3, env4, env5, env6);
    }

    public static List<String> getTopics() {
        return List.of("topic1", "topic2", "topic3");
    }

    public static Map<String, String> getEncodedSensitiveEnvs() {
        return Map.of(
            "env2", "dmFsMg==",
            "env3", "ewogIHRva2VuOiAxMjMKfQ==",
            "env5", "ZmlsZS1jb250ZW50LTU=",
            "env6", "ewogIHRva2VuOiAxMjMKfQ=="
        );
    }

    private static DeploymentMetadata createMetadata() {
        var env1 = new EnvVarDefinition("env1", new SimpleEnvVarValue("test-value-1"),
                EnvVarMountType.CONTENT, "Simple value & content mount type");
        var env2 = new EnvVarDefinition("env2", new SimpleEnvVarValue("val2"),
                EnvVarMountType.SECURE_CONTENT, "Simple value & secure content mount type");
        var env3 = new EnvVarDefinition("env3", new SimpleEnvVarValue("ewogIHRva2VuOiAxMjMKfQ=="),
                EnvVarMountType.SECURE_FILE, "Simple value & secure file mount type");
        var env4 = new EnvVarDefinition("env4", new FileEnvVarValue("file4.json", "some-file-content-4"),
                EnvVarMountType.CONTENT, "File value & content mount type");
        var env5 = new EnvVarDefinition("env5", new FileEnvVarValue("file5.json", "file-content-5"),
                EnvVarMountType.SECURE_CONTENT, "File value & secure content mount type");
        var env6 = new EnvVarDefinition("env6", new FileEnvVarValue("file6.json", "ewogIHRva2VuOiAxMjMKfQ=="),
                EnvVarMountType.SECURE_FILE, "File value & secure file mount type");
        return new DeploymentMetadata(List.of(env1, env2, env3, env4, env5, env6));
    }

    private static Resources createResources() {
        Map<String, String> limits = Map.of("cpu", "1", "memory", "1Gi");
        Map<String, String> requests = Map.of("cpu", "500m", "memory", "512Mi");
        return new Resources(limits, requests);
    }
}
