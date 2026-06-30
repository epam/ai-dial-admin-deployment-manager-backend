package com.epam.aidial.deployment.manager.service;

import com.epam.aidial.deployment.manager.cleanup.resource.DisposableResourceManager;
import com.epam.aidial.deployment.manager.cleanup.resource.model.K8sResourceKind;
import com.epam.aidial.deployment.manager.cleanup.resource.model.K8sResourceReference;
import com.epam.aidial.deployment.manager.cleanup.resource.model.ResourceLifecycleState;
import com.epam.aidial.deployment.manager.configuration.logging.LogExecution;
import com.epam.aidial.deployment.manager.kubernetes.K8sClient;
import com.epam.aidial.deployment.manager.service.manifest.ManifestGenerator;
import com.epam.aidial.deployment.manager.utils.K8sNamingUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Provisions a per-deployment docker pull secret at deploy time when an in-scope image is served from
 * a configured credentialed registry, so the generated workload can reference it via
 * {@code imagePullSecrets} without any manual administrator action.
 *
 * <p>Mirrors the sensitive-env-var secret pattern in
 * {@link com.epam.aidial.deployment.manager.service.deployment.AbstractDeploymentManager}: the secret
 * is named deterministically per deployment, created via {@link K8sClient}, and registered with the
 * {@link DisposableResourceManager} so it is cleaned up with the deployment. It is therefore created
 * within the calling deploy transaction.
 */
@Slf4j
@Service
@LogExecution
public class RegistryPullSecretProvisioner {

    private static final String PULL_SECRET_NAME_SUFFIX = "pull";

    private final boolean enabled;
    private final RegistryService registryService;
    private final ManifestGenerator manifestGenerator;
    private final K8sClient k8sClient;
    private final DisposableResourceManager disposableResourceManager;

    public RegistryPullSecretProvisioner(
            @Value("${app.registry.auto-pull-secret-enabled}") boolean enabled,
            RegistryService registryService,
            ManifestGenerator manifestGenerator,
            K8sClient k8sClient,
            DisposableResourceManager disposableResourceManager) {
        this.enabled = enabled;
        this.registryService = registryService;
        this.manifestGenerator = manifestGenerator;
        this.k8sClient = k8sClient;
        this.disposableResourceManager = disposableResourceManager;
    }

    /**
     * Ensure a docker pull secret exists for {@code deploymentId} when any of {@code inScopeImages} is
     * served from a configured credentialed registry, and return its name so the caller can wire it
     * into the generated workload's {@code imagePullSecrets}.
     *
     * @return the provisioned secret name, or {@link Optional#empty()} when the feature is disabled,
     *         no images are supplied, or none match a credentialed registry — in which case nothing is
     *         created and no {@code imagePullSecrets} reference should be injected
     */
    public Optional<String> provisionForDeployment(String deploymentId, String namespace, Collection<String> inScopeImages) {
        if (!enabled) {
            log.debug("Auto pull-secret provisioning is disabled; skipping for deployment '{}'", deploymentId);
            return Optional.empty();
        }
        if (CollectionUtils.isEmpty(inScopeImages)) {
            return Optional.empty();
        }

        // Narrowed to the registries that serve THIS deployment's images — not the full build-time
        // dockerConfig() aggregate. Empty when no in-scope image matches a credentialed registry.
        var dockerConfig = registryService.dockerConfigForImages(inScopeImages);
        if (dockerConfig.isEmpty()) {
            log.debug("No in-scope image of deployment '{}' matches a credentialed configured registry; "
                    + "no pull secret provisioned", deploymentId);
            return Optional.empty();
        }

        var secretName = K8sNamingUtils.generateUniqueName(deploymentId, PULL_SECRET_NAME_SUFFIX);
        var secret = manifestGenerator.pullSecretConfig(secretName, dockerConfig.get());

        disposableResourceManager.saveK8sResources(List.of(secret), K8sResourceKind.SECRET, deploymentId, namespace);
        k8sClient.createSecret(namespace, secret);
        disposableResourceManager.changeResourceLifecycleByGroupIdInSameTransaction(
                deploymentId,
                new K8sResourceReference(namespace, K8sResourceKind.SECRET, secretName),
                ResourceLifecycleState.STABLE);

        log.info("Provisioned docker pull secret '{}' for deployment '{}' in namespace '{}'",
                secretName, deploymentId, namespace);
        return Optional.of(secretName);
    }
}
