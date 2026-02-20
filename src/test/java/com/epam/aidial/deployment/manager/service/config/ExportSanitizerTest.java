package com.epam.aidial.deployment.manager.service.config;

import com.epam.aidial.deployment.manager.configuration.JsonMapperConfiguration;
import com.epam.aidial.deployment.manager.model.EnvVar;
import com.epam.aidial.deployment.manager.model.SensitiveEnvVar;
import com.epam.aidial.deployment.manager.model.SensitiveFileEnvVar;
import com.epam.aidial.deployment.manager.model.SimpleEnvVar;
import com.epam.aidial.deployment.manager.model.SimpleEnvVarValue;
import com.epam.aidial.deployment.manager.model.deployment.Deployment;
import com.epam.aidial.deployment.manager.model.deployment.McpDeployment;
import com.epam.aidial.deployment.manager.model.McpTransport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = ExportSanitizer.class)
@Import(JsonMapperConfiguration.class)
class ExportSanitizerTest {

    @Autowired
    private ExportSanitizer exportSanitizer;

    @Test
    void sanitizeDeploymentForExport_addSecretsTrue_returnsCopyWithEnvsUnchanged() {
        SensitiveEnvVar sensitive = SensitiveEnvVar.builder()
                .name("A")
                .value(new SimpleEnvVarValue("v1"))
                .build();
        McpDeployment source = McpDeployment.builder()
                .id("dep-1")
                .envs(List.of(sensitive))
                .transport(McpTransport.SSE)
                .build();

        Deployment result = exportSanitizer.sanitizeDeploymentForExport(source, true);

        assertThat(result).isNotSameAs(source);
        assertThat(result.getEnvs()).hasSize(1);
        assertThat(result.getEnvs().getFirst().getName()).isEqualTo("A");
        assertThat(result.getEnvs().getFirst().getValue().getValue()).isEqualTo("v1");
    }

    @Test
    void sanitizeDeploymentForExport_emptyEnvs_returnsCopy() {
        McpDeployment source = McpDeployment.builder()
                .id("dep-2")
                .envs(List.of())
                .transport(McpTransport.SSE)
                .build();

        Deployment result = exportSanitizer.sanitizeDeploymentForExport(source, false);

        assertThat(result).isNotSameAs(source);
        assertThat(result.getEnvs()).isEmpty();
    }

    @Test
    void sanitizeDeploymentForExport_nullEnvs_returnsCopy() {
        McpDeployment source = McpDeployment.builder()
                .id("dep-2n")
                .envs(null)
                .transport(McpTransport.SSE)
                .build();

        Deployment result = exportSanitizer.sanitizeDeploymentForExport(source, false);

        assertThat(result).isNotSameAs(source);
        assertThat(result.getEnvs()).isNull();
    }

    @Test
    void sanitizeDeploymentForExport_addSecretsFalse_sensitiveValuesCleared() {
        // Given
        SensitiveEnvVar sensitive = SensitiveEnvVar.builder()
                .name("SECRET")
                .value(new SimpleEnvVarValue("my-secret"))
                .k8sSecretName("s1")
                .k8sSecretKey("k1")
                .build();
        SensitiveEnvVar sensitiveFile = SensitiveFileEnvVar.builder()
                .name("SECRET_FILE")
                .value(new SimpleEnvVarValue("my-secret-file"))
                .k8sSecretName("s1")
                .k8sSecretKey("k2")
                .build();
        McpDeployment source = McpDeployment.builder()
                .id("dep-3")
                .envs(List.of(sensitive, sensitiveFile))
                .transport(McpTransport.SSE)
                .build();

        // When
        Deployment result = exportSanitizer.sanitizeDeploymentForExport(source, false);

        // Then
        assertThat(result).isNotSameAs(source);
        assertThat(result.getEnvs()).hasSize(2);

        EnvVar sensitiveOut = result.getEnvs().get(0);
        EnvVar sensitiveFileOut = result.getEnvs().get(1);

        assertThat(sensitiveOut).isInstanceOf(SensitiveEnvVar.class);
        assertThat(sensitiveOut.getName()).isEqualTo("SECRET");
        assertThat(sensitiveOut.getValue()).isNull();
        assertThat(((SensitiveEnvVar) sensitiveOut).getK8sSecretName()).isNull();
        assertThat(((SensitiveEnvVar) sensitiveOut).getK8sSecretKey()).isNull();

        assertThat(sensitiveFileOut).isInstanceOf(SensitiveFileEnvVar.class);
        assertThat(sensitiveFileOut.getName()).isEqualTo("SECRET_FILE");
        assertThat(sensitiveFileOut.getValue()).isNull();
        assertThat(((SensitiveFileEnvVar) sensitiveFileOut).getK8sSecretName()).isNull();
        assertThat(((SensitiveFileEnvVar) sensitiveFileOut).getK8sSecretKey()).isNull();
    }

    @Test
    void sanitizeDeploymentForExport_addSecretsFalse_plainEnvsUnchanged() {
        SimpleEnvVar plain = SimpleEnvVar.builder()
                .name("PLAIN")
                .value(new SimpleEnvVarValue("visible"))
                .build();
        McpDeployment source = McpDeployment.builder()
                .id("dep-4")
                .envs(List.of(plain))
                .transport(McpTransport.SSE)
                .build();

        Deployment result = exportSanitizer.sanitizeDeploymentForExport(source, false);

        assertThat(result.getEnvs()).hasSize(1);
        assertThat(result.getEnvs().getFirst().getName()).isEqualTo("PLAIN");
        assertThat(result.getEnvs().getFirst().getValue().getValue()).isEqualTo("visible");
    }

    @Test
    void sanitizeDeploymentForExport_preservesDeploymentSubtype() {
        McpDeployment source = McpDeployment.builder()
                .id("dep-5")
                .transport(McpTransport.HTTP_STREAMING)
                .mcpEndpointPath("/mcp")
                .build();

        Deployment result = exportSanitizer.sanitizeDeploymentForExport(source, true);

        assertThat(result).isInstanceOf(McpDeployment.class);
        assertThat(((McpDeployment) result).getTransport()).isEqualTo(McpTransport.HTTP_STREAMING);
        assertThat(((McpDeployment) result).getMcpEndpointPath()).isEqualTo("/mcp");
    }
}
