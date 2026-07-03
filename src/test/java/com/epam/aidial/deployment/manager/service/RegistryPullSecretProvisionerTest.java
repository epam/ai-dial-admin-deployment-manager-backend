package com.epam.aidial.deployment.manager.service;

import com.epam.aidial.deployment.manager.cleanup.resource.DisposableResourceManager;
import com.epam.aidial.deployment.manager.cleanup.resource.model.DisposableResource;
import com.epam.aidial.deployment.manager.cleanup.resource.model.K8sResourceKind;
import com.epam.aidial.deployment.manager.cleanup.resource.model.K8sResourceReference;
import com.epam.aidial.deployment.manager.cleanup.resource.model.ResourceLifecycleState;
import com.epam.aidial.deployment.manager.kubernetes.K8sClient;
import com.epam.aidial.deployment.manager.service.manifest.ManifestGenerator;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.SecretBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RegistryPullSecretProvisionerTest {

    private static final String DEPLOYMENT_ID = "dep-1";
    private static final String NAMESPACE = "ns";
    private static final String DOCKER_CONFIG_JSON = "{\"auths\":{\"priv.reg\":{\"auth\":\"eA==\"}}}";

    @Mock
    private RegistryService registryService;
    @Mock
    private ManifestGenerator manifestGenerator;
    @Mock
    private K8sClient k8sClient;
    @Mock
    private DisposableResourceManager disposableResourceManager;

    private RegistryPullSecretProvisioner newProvisioner(boolean enabled) {
        return new RegistryPullSecretProvisioner(enabled, registryService, manifestGenerator, k8sClient, disposableResourceManager);
    }

    @Test
    void shouldReturnEmptyAndDoNothing_whenFeatureDisabled() {
        var provisioner = newProvisioner(false);

        var result = provisioner.provisionForDeployment(DEPLOYMENT_ID, NAMESPACE, List.of("priv.reg/app:1"));

        assertThat(result).isEmpty();
        verifyNoInteractions(registryService, manifestGenerator, k8sClient, disposableResourceManager);
    }

    @Test
    void shouldReturnEmpty_whenNoInScopeImages() {
        var provisioner = newProvisioner(true);

        var result = provisioner.provisionForDeployment(DEPLOYMENT_ID, NAMESPACE, List.of());

        assertThat(result).isEmpty();
        verify(k8sClient, never()).createSecret(any(), any());
    }

    @Test
    void shouldReturnEmptyAndCreateNoSecret_whenNoImageMatchesCredentialedRegistry() {
        var provisioner = newProvisioner(true);
        when(registryService.dockerConfigForImages(List.of("public.reg/app:1"))).thenReturn(Optional.empty());

        var result = provisioner.provisionForDeployment(DEPLOYMENT_ID, NAMESPACE, List.of("public.reg/app:1"));

        assertThat(result).isEmpty();
        verify(k8sClient, never()).createSecret(any(), any());
        verify(disposableResourceManager, never()).saveK8sResources(any(), any(), anyString(), anyString());
    }

    @Test
    void shouldCreateSecretFromNarrowedDockerConfigAndRegisterDisposable_whenImageMatches() {
        var provisioner = newProvisioner(true);
        var secret = new SecretBuilder().withNewMetadata().withName("test-dep-1-pull-abcdef").endMetadata().build();
        when(registryService.dockerConfigForImages(List.of("priv.reg/app:1"))).thenReturn(Optional.of(DOCKER_CONFIG_JSON));
        when(manifestGenerator.pullSecretConfig(anyString(), eq(DOCKER_CONFIG_JSON))).thenReturn(secret);

        var result = provisioner.provisionForDeployment(DEPLOYMENT_ID, NAMESPACE, List.of("priv.reg/app:1"));

        assertThat(result).isPresent();
        verify(disposableResourceManager)
                .saveK8sResources(eq(List.of(secret)), eq(K8sResourceKind.SECRET), eq(DEPLOYMENT_ID), eq(NAMESPACE));
        verify(k8sClient).createSecret(eq(NAMESPACE), eq(secret));
        verify(disposableResourceManager)
                .changeResourceLifecycleByGroupIdInSameTransaction(eq(DEPLOYMENT_ID), any(), eq(ResourceLifecycleState.STABLE));
    }

    @Test
    void shouldSupersedePriorPullSecret_butLeaveEnvSecretUntouched_whenImageMatches() {
        var provisioner = newProvisioner(true);
        var priorPullSecret = disposableSecret("test-dep-1-pull-oldsec");
        var envSecret = disposableSecret("test-dep-1-envs-abcdef");
        var newSecret = new SecretBuilder().withNewMetadata().withName("test-dep-1-pull-newone").endMetadata().build();
        when(disposableResourceManager.getAllByGroupId(DEPLOYMENT_ID)).thenReturn(List.of(priorPullSecret, envSecret));
        when(registryService.dockerConfigForImages(List.of("priv.reg/app:1"))).thenReturn(Optional.of(DOCKER_CONFIG_JSON));
        when(manifestGenerator.pullSecretConfig(anyString(), eq(DOCKER_CONFIG_JSON))).thenReturn(newSecret);

        var result = provisioner.provisionForDeployment(DEPLOYMENT_ID, NAMESPACE, List.of("priv.reg/app:1"));

        assertThat(result).isPresent();
        // The prior pull secret is marked for cleanup so it is not orphaned.
        verify(disposableResourceManager).changeResourceLifecycleByGroupIdInSameTransaction(
                eq(DEPLOYMENT_ID),
                argThat(ref -> ref instanceof K8sResourceReference k8s && "test-dep-1-pull-oldsec".equals(k8s.getName())),
                eq(ResourceLifecycleState.TO_CLEANUP));
        // The co-located env secret is never touched by pull-secret supersession.
        verify(disposableResourceManager, never()).changeResourceLifecycleByGroupIdInSameTransaction(
                anyString(),
                argThat(ref -> ref instanceof K8sResourceReference k8s && "test-dep-1-envs-abcdef".equals(k8s.getName())),
                any());
        // The freshly provisioned secret still ends up STABLE.
        verify(disposableResourceManager).changeResourceLifecycleByGroupIdInSameTransaction(
                eq(DEPLOYMENT_ID), any(), eq(ResourceLifecycleState.STABLE));
        verify(k8sClient).createSecret(eq(NAMESPACE), eq(newSecret));
    }

    private static DisposableResource disposableSecret(String name) {
        return DisposableResource.builder()
                .groupId(DEPLOYMENT_ID)
                .reference(new K8sResourceReference(NAMESPACE, K8sResourceKind.SECRET, name))
                .lifecycleState(ResourceLifecycleState.STABLE)
                .build();
    }
}
