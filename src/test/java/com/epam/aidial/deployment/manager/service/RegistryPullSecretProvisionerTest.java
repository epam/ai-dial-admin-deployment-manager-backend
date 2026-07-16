package com.epam.aidial.deployment.manager.service;

import com.epam.aidial.deployment.manager.cleanup.resource.DisposableResourceManager;
import com.epam.aidial.deployment.manager.cleanup.resource.model.DisposableResource;
import com.epam.aidial.deployment.manager.cleanup.resource.model.K8sResourceKind;
import com.epam.aidial.deployment.manager.cleanup.resource.model.K8sResourceReference;
import com.epam.aidial.deployment.manager.cleanup.resource.model.ResourceLifecycleState;
import com.epam.aidial.deployment.manager.kubernetes.K8sClient;
import com.epam.aidial.deployment.manager.service.RegistryPullSecretProvisioner.PullSecretPlan;
import com.epam.aidial.deployment.manager.service.manifest.ManifestGenerator;
import com.epam.aidial.deployment.manager.utils.K8sNamingUtils;
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
    // Deterministic per-deployment pull-secret name the provisioner must always produce (no random suffix).
    private static final String PULL_SECRET_NAME = K8sNamingUtils.generateName(DEPLOYMENT_ID, "pull");

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

    // ---------- plan(): read-only, no cluster/DB writes ----------

    @Test
    void plan_shouldReturnNoneAndDoNothing_whenFeatureDisabled() {
        var plan = newProvisioner(false).plan(DEPLOYMENT_ID, NAMESPACE, List.of("priv.reg/app:1"));

        assertThat(plan.action()).isEqualTo(PullSecretPlan.Action.NONE);
        verifyNoInteractions(registryService, manifestGenerator, k8sClient, disposableResourceManager);
    }

    @Test
    void plan_shouldReturnCondemn_whenNoInScopeImages() {
        var plan = newProvisioner(true).plan(DEPLOYMENT_ID, NAMESPACE, List.of());

        // A managed deployment with no image to cover → clean up any stale pull secret on apply().
        assertThat(plan.action()).isEqualTo(PullSecretPlan.Action.CONDEMN);
        verifyNoInteractions(k8sClient, disposableResourceManager);
    }

    @Test
    void plan_shouldReturnCondemn_whenNoImageMatchesCredentialedRegistry() {
        when(registryService.dockerConfigForImages(List.of("public.reg/app:1"))).thenReturn(Optional.empty());

        var plan = newProvisioner(true).plan(DEPLOYMENT_ID, NAMESPACE, List.of("public.reg/app:1"));

        assertThat(plan.action()).isEqualTo(PullSecretPlan.Action.CONDEMN);
        verifyNoInteractions(k8sClient, disposableResourceManager);
    }

    @Test
    void plan_shouldReturnProvisionWithDeterministicName_andWriteNothing_whenImageMatches() {
        var secret = secretNamed(PULL_SECRET_NAME);
        when(registryService.dockerConfigForImages(List.of("priv.reg/app:1"))).thenReturn(Optional.of(DOCKER_CONFIG_JSON));
        when(manifestGenerator.pullSecretConfig(eq(PULL_SECRET_NAME), eq(DOCKER_CONFIG_JSON))).thenReturn(secret);

        var plan = newProvisioner(true).plan(DEPLOYMENT_ID, NAMESPACE, List.of("priv.reg/app:1"));

        assertThat(plan.action()).isEqualTo(PullSecretPlan.Action.PROVISION);
        assertThat(plan.secretName()).isEqualTo(PULL_SECRET_NAME);
        assertThat(PULL_SECRET_NAME).doesNotMatch(".*-pull-[a-z]{6}"); // stable, not random-suffixed (#387)
        assertThat(plan.secret()).isSameAs(secret);
        // plan() is read-only: no cluster secret, no disposable-resource bookkeeping.
        verifyNoInteractions(k8sClient, disposableResourceManager);
    }

    // ---------- apply(): provision-only, post-commit before the CRD apply ----------

    @Test
    void apply_shouldDoNothing_forNonePlan() {
        newProvisioner(true).apply(DEPLOYMENT_ID, NAMESPACE, PullSecretPlan.none());

        verifyNoInteractions(k8sClient, disposableResourceManager);
    }

    @Test
    void apply_shouldDoNothing_forCondemnPlan() {
        // Condemnation is deferred to condemnStale(), which runs only after the CRD apply succeeded.
        newProvisioner(true).apply(DEPLOYMENT_ID, NAMESPACE, PullSecretPlan.condemn());

        verifyNoInteractions(k8sClient, disposableResourceManager);
    }

    @Test
    void apply_shouldCreateOrReplaceAndSaveDirectlyStable_forProvisionPlan_firstTime() {
        var secret = secretNamed(PULL_SECRET_NAME);

        newProvisioner(true).apply(DEPLOYMENT_ID, NAMESPACE, PullSecretPlan.provision(PULL_SECRET_NAME, secret));

        verify(k8sClient).createOrReplaceSecret(eq(NAMESPACE), eq(secret));
        // Saved directly STABLE in one transaction — never a TEMPORARY row the scheduled cleaner
        // (which reclaims all TEMPORARY rows with no age grace) could reap before a separate promote.
        verify(disposableResourceManager).saveK8sResources(
                eq(List.of(secret)), eq(K8sResourceKind.SECRET), eq(DEPLOYMENT_ID), eq(NAMESPACE),
                eq(ResourceLifecycleState.STABLE));
        verify(disposableResourceManager, never()).saveK8sResources(any(), any(), anyString(), anyString());
        // apply() never condemns anything.
        verify(disposableResourceManager, never())
                .changeResourceLifecycleByGroupId(anyString(), any(), eq(ResourceLifecycleState.TO_CLEANUP));
    }

    @Test
    void apply_shouldReaffirmStableWithoutDuplicateRow_whenDeterministicSecretAlreadyTracked() {
        var secret = secretNamed(PULL_SECRET_NAME);
        when(disposableResourceManager.getAllByGroupId(DEPLOYMENT_ID))
                .thenReturn(List.of(disposableSecret(PULL_SECRET_NAME)));

        newProvisioner(true).apply(DEPLOYMENT_ID, NAMESPACE, PullSecretPlan.provision(PULL_SECRET_NAME, secret));

        verify(k8sClient).createOrReplaceSecret(eq(NAMESPACE), eq(secret));
        verify(disposableResourceManager, never()).saveK8sResources(any(), any(), anyString(), anyString());
        verify(disposableResourceManager, never()).saveK8sResources(any(), any(), anyString(), anyString(), any());
        verify(disposableResourceManager, never())
                .changeResourceLifecycleByGroupId(anyString(), any(), eq(ResourceLifecycleState.TO_CLEANUP));
        // Re-affirmed STABLE — also resurrects a TO_CLEANUP row left by a prior undeploy.
        verify(disposableResourceManager).changeResourceLifecycleByGroupId(
                eq(DEPLOYMENT_ID),
                argThat(ref -> ref instanceof K8sResourceReference k8s && PULL_SECRET_NAME.equals(k8s.getName())),
                eq(ResourceLifecycleState.STABLE));
    }

    // ---------- condemnStale(): post-commit, only after the CRD apply succeeded ----------

    @Test
    void condemnStale_shouldDoNothing_forNonePlan() {
        newProvisioner(true).condemnStale(DEPLOYMENT_ID, NAMESPACE, PullSecretPlan.none());

        verifyNoInteractions(k8sClient, disposableResourceManager);
    }

    @Test
    void condemnStale_shouldMigrateLegacyRandomNamedPullSecret_butLeaveEnvSecretUntouched_forProvisionPlan() {
        when(disposableResourceManager.getAllByGroupId(DEPLOYMENT_ID))
                .thenReturn(List.of(disposableSecret("dm-dep-1-pull-oldsec"), disposableSecret("dm-dep-1-envs-abcdef")));

        newProvisioner(true).condemnStale(DEPLOYMENT_ID, NAMESPACE, PullSecretPlan.provision(PULL_SECRET_NAME, secretNamed(PULL_SECRET_NAME)));

        // Legacy random-named pull secret is condemned (migrated), never orphaned.
        verify(disposableResourceManager).changeResourceLifecycleByGroupId(
                eq(DEPLOYMENT_ID),
                argThat(ref -> ref instanceof K8sResourceReference k8s && "dm-dep-1-pull-oldsec".equals(k8s.getName())),
                eq(ResourceLifecycleState.TO_CLEANUP));
        // Co-located env secret is never touched by pull-secret handling.
        verify(disposableResourceManager, never()).changeResourceLifecycleByGroupId(
                anyString(),
                argThat(ref -> ref instanceof K8sResourceReference k8s && "dm-dep-1-envs-abcdef".equals(k8s.getName())),
                any());
        // condemnStale() never writes a secret.
        verify(k8sClient, never()).createOrReplaceSecret(any(), any());
    }

    @Test
    void condemnStale_shouldKeepDeterministicName_forProvisionPlan() {
        when(disposableResourceManager.getAllByGroupId(DEPLOYMENT_ID))
                .thenReturn(List.of(disposableSecret(PULL_SECRET_NAME)));

        newProvisioner(true).condemnStale(DEPLOYMENT_ID, NAMESPACE, PullSecretPlan.provision(PULL_SECRET_NAME, secretNamed(PULL_SECRET_NAME)));

        // The name the just-applied spec references is never condemned.
        verify(disposableResourceManager, never())
                .changeResourceLifecycleByGroupId(anyString(), any(), eq(ResourceLifecycleState.TO_CLEANUP));
    }

    @Test
    void condemnStale_shouldCondemnAllPullSecrets_andWriteNoSecret_forCondemnPlan() {
        when(disposableResourceManager.getAllByGroupId(DEPLOYMENT_ID)).thenReturn(List.of(
                disposableSecret("dm-dep-1-pull-oldsec"),
                disposableSecret(PULL_SECRET_NAME),
                disposableSecret("dm-dep-1-envs-abcdef")));

        newProvisioner(true).condemnStale(DEPLOYMENT_ID, NAMESPACE, PullSecretPlan.condemn());

        // Both the legacy and the deterministic pull secret are condemned (keepName = null).
        verify(disposableResourceManager).changeResourceLifecycleByGroupId(
                eq(DEPLOYMENT_ID),
                argThat(ref -> ref instanceof K8sResourceReference k8s && "dm-dep-1-pull-oldsec".equals(k8s.getName())),
                eq(ResourceLifecycleState.TO_CLEANUP));
        verify(disposableResourceManager).changeResourceLifecycleByGroupId(
                eq(DEPLOYMENT_ID),
                argThat(ref -> ref instanceof K8sResourceReference k8s && PULL_SECRET_NAME.equals(k8s.getName())),
                eq(ResourceLifecycleState.TO_CLEANUP));
        // Env secret untouched; no secret written; no STABLE tracking.
        verify(disposableResourceManager, never()).changeResourceLifecycleByGroupId(
                anyString(),
                argThat(ref -> ref instanceof K8sResourceReference k8s && "dm-dep-1-envs-abcdef".equals(k8s.getName())),
                any());
        verify(k8sClient, never()).createOrReplaceSecret(any(), any());
        verify(disposableResourceManager, never()).saveK8sResources(any(), any(), anyString(), anyString());
        verify(disposableResourceManager, never()).saveK8sResources(any(), any(), anyString(), anyString(), any());
    }

    private static Secret secretNamed(String name) {
        return new SecretBuilder().withNewMetadata().withName(name).endMetadata().build();
    }

    private static DisposableResource disposableSecret(String name) {
        return DisposableResource.builder()
                .groupId(DEPLOYMENT_ID)
                .reference(new K8sResourceReference(NAMESPACE, K8sResourceKind.SECRET, name))
                .lifecycleState(ResourceLifecycleState.STABLE)
                .build();
    }
}
