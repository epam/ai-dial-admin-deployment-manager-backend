package com.epam.aidial.deployment.manager.service;

import com.epam.aidial.deployment.manager.cleanup.resource.DisposableResourceManager;
import com.epam.aidial.deployment.manager.cleanup.resource.model.DisposableResource;
import com.epam.aidial.deployment.manager.cleanup.resource.model.K8sResourceKind;
import com.epam.aidial.deployment.manager.cleanup.resource.model.K8sResourceReference;
import com.epam.aidial.deployment.manager.cleanup.resource.model.ResourceLifecycleState;
import com.epam.aidial.deployment.manager.configuration.logging.LogExecution;
import com.epam.aidial.deployment.manager.kubernetes.K8sClient;
import com.epam.aidial.deployment.manager.service.manifest.ManifestGenerator;
import com.epam.aidial.deployment.manager.utils.K8sNamingUtils;
import io.fabric8.kubernetes.api.model.Secret;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;

/**
 * Provisions a per-deployment docker pull secret when an in-scope image is served from a configured
 * credentialed registry, so the generated workload can reference it via {@code imagePullSecrets}
 * without any manual administrator action.
 *
 * <p>Provisioning is three-phase, mirroring how the {@code CiliumNetworkPolicy} is handled:
 * <ol>
 *   <li>{@link #plan} — read-only, safe to run inside the deploy transaction (pre-commit). It resolves
 *       whether a pull secret is needed, and if so computes the <b>deterministic</b> per-deployment name
 *       ({@code <prefix>-<id>-pull}) and secret manifest. No cluster or DB writes.</li>
 *   <li>{@link #apply} — run in the {@code afterCommit} phase right before the CRD is applied. It
 *       create-or-replaces the secret in place (idempotent across redeploys, so a name a live or
 *       scaled-to-zero revision references is never orphaned) and tracks it {@code STABLE} via the
 *       {@link DisposableResourceManager}.</li>
 *   <li>{@link #condemnStale} — run only <b>after</b> the CRD apply succeeded. It condemns pull
 *       secrets the just-applied spec no longer references (legacy random-named ones, or all of them
 *       when the image moved to a public/unconfigured registry). Sequenced last so a failed CRD apply
 *       never condemns a secret the still-live revision references — the worst case of a failure in
 *       between is a leaked secret, reclaimed on the next successful redeploy.</li>
 * </ol>
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
     * Read-only planning phase. Decide whether {@code inScopeImages} require a pull secret and, if so,
     * compute the deterministic name and secret manifest. Performs <b>no</b> cluster or DB writes, so it
     * is safe to call inside the deploy transaction while building the workload spec.
     *
     * @return a {@link PullSecretPlan} the caller wires into the spec (via {@link PullSecretPlan#secretName()})
     *         and later hands to {@link #apply} in the post-commit phase
     */
    public PullSecretPlan plan(String deploymentId, String namespace, Collection<String> inScopeImages) {
        if (!enabled) {
            log.debug("Auto pull-secret provisioning is disabled; skipping for deployment '{}'", deploymentId);
            return PullSecretPlan.none();
        }
        if (CollectionUtils.isEmpty(inScopeImages)) {
            // Managed deployment with no in-scope image to cover → ensure no stale pull secret lingers.
            return PullSecretPlan.condemn();
        }

        // Narrowed to the registries that serve THIS deployment's images — not the full build-time
        // dockerConfig() aggregate. Empty when no in-scope image matches a credentialed registry.
        var dockerConfig = registryService.dockerConfigForImages(inScopeImages);
        if (dockerConfig.isEmpty()) {
            log.debug("No in-scope image of deployment '{}' matches a credentialed configured registry; "
                    + "any prior pull secret will be condemned", deploymentId);
            return PullSecretPlan.condemn();
        }

        // Deterministic per-deployment name so redeploy performs an idempotent create-or-replace of the
        // SAME object the live workload already references — instead of minting a new random name and
        // orphaning the old one, which a scaled-to-zero Knative revision could still reference (#387).
        var secretName = K8sNamingUtils.generateName(deploymentId, PULL_SECRET_NAME_SUFFIX);
        var secret = manifestGenerator.pullSecretConfig(secretName, dockerConfig.get());
        return PullSecretPlan.provision(secretName, secret);
    }

    /**
     * Provision-only apply phase. MUST run in the post-commit phase (an {@code afterCommit} hook),
     * right before the CRD is applied — mirroring how the {@code CiliumNetworkPolicy} is applied — so a
     * rolled-back deploy never mutates the cluster secret and the secret write lands together with the
     * CRD apply. Uses the {@code REQUIRES_NEW} disposable-resource bookkeeping (there is no ambient
     * transaction post-commit). Deliberately condemns nothing — see {@link #condemnStale}.
     */
    public void apply(String deploymentId, String namespace, PullSecretPlan plan) {
        switch (plan.action()) {
            case NONE, CONDEMN -> {
                // nothing to provision; stale-secret condemnation is deferred to condemnStale()
            }
            case PROVISION -> {
                k8sClient.createOrReplaceSecret(namespace, plan.secret());
                markPullSecretStable(deploymentId, namespace, plan.secret(), plan.secretName());
                log.info("Provisioned docker pull secret '{}' for deployment '{}' in namespace '{}'",
                        plan.secretName(), deploymentId, namespace);
            }
            default -> throw new IllegalStateException("Unexpected pull-secret action: " + plan.action());
        }
    }

    /**
     * Condemnation phase. MUST run only after the CRD apply succeeded: condemning earlier would let the
     * cleaner delete a secret the still-live (possibly scaled-to-zero) revision references if the
     * create/update of the CRD failed — reintroducing #387 on the failure path. For {@code PROVISION}
     * plans it condemns only prior differently-named (legacy) secrets; for {@code CONDEMN} plans (the
     * image no longer needs credentials) it condemns every tracked pull secret of the deployment.
     */
    public void condemnStale(String deploymentId, String namespace, PullSecretPlan plan) {
        switch (plan.action()) {
            case NONE -> {
                // feature disabled / unmanaged deployment — leave any existing secrets untouched
            }
            case CONDEMN -> condemnPriorPullSecrets(deploymentId, namespace, null);
            case PROVISION -> condemnPriorPullSecrets(deploymentId, namespace, plan.secretName());
            default -> throw new IllegalStateException("Unexpected pull-secret action: " + plan.action());
        }
    }

    /**
     * Mark previously provisioned pull secret(s) for this deployment {@code TO_CLEANUP} so the cleanup
     * job reclaims the orphaned K8s secret and its {@code disposable_resource} row — but never the one
     * named {@code keepName} (the deterministic name the current spec references; pass {@code null} to
     * condemn all). In steady state the only tracked pull secret already uses {@code keepName}, so this
     * is a no-op; it fires to migrate a legacy {@code -pull-<random>} secret, or to clean up when an
     * update moves the image to a public/unconfigured registry.
     */
    private void condemnPriorPullSecrets(String deploymentId, String namespace, String keepName) {
        disposableResourceManager.getAllByGroupId(deploymentId).stream()
                .map(DisposableResource::getReference)
                .filter(K8sResourceReference.class::isInstance)
                .map(K8sResourceReference.class::cast)
                .filter(ref -> ref.getKind() == K8sResourceKind.SECRET && isPullSecret(ref.getName()))
                .filter(ref -> keepName == null || !keepName.equals(ref.getName()))
                .forEach(ref -> disposableResourceManager.changeResourceLifecycleByGroupId(
                        deploymentId,
                        new K8sResourceReference(namespace, K8sResourceKind.SECRET, ref.getName()),
                        ResourceLifecycleState.TO_CLEANUP));
    }

    /**
     * Register the pull secret as a {@code STABLE} disposable resource, idempotently: on the first
     * deploy it is saved directly {@code STABLE} in a single transaction — never as an externally
     * visible {@code TEMPORARY} row the scheduled cleaner (which reclaims all {@code TEMPORARY} rows
     * with no age grace) could reap between a save and a separate promote. On redeploy the existing row
     * for the deterministic name is (re)affirmed {@code STABLE}, so no duplicate row is inserted and a
     * {@code TO_CLEANUP} row from a prior undeploy is resurrected.
     */
    private void markPullSecretStable(String deploymentId, String namespace, Secret secret, String secretName) {
        var reference = new K8sResourceReference(namespace, K8sResourceKind.SECRET, secretName);
        var alreadyTracked = disposableResourceManager.getAllByGroupId(deploymentId).stream()
                .map(DisposableResource::getReference)
                .anyMatch(reference::equals);
        if (alreadyTracked) {
            disposableResourceManager.changeResourceLifecycleByGroupId(
                    deploymentId, reference, ResourceLifecycleState.STABLE);
        } else {
            disposableResourceManager.saveK8sResources(
                    List.of(secret), K8sResourceKind.SECRET, deploymentId, namespace, ResourceLifecycleState.STABLE);
        }
    }

    /**
     * Pull secrets are named {@code <prefix>-<deploymentId>-pull} by {@link K8sNamingUtils#generateName}
     * (or the legacy {@code <prefix>-<deploymentId>-pull-<6 lowercase>} from before the
     * deterministic-naming fix). Env secrets use the {@code -envs-} marker, so matching the {@code -pull}
     * tail with an optional random suffix keeps env secrets of the same deployment untouched.
     */
    private static boolean isPullSecret(String name) {
        return name != null && name.matches(".*-" + PULL_SECRET_NAME_SUFFIX + "(-[a-z]{6})?");
    }

    /**
     * Outcome of {@link #plan}: {@code PROVISION} the deterministically named pull secret (create-or-replace),
     * {@code CONDEMN} any prior pull secret (the deployment's current image needs no credentials), or
     * {@code NONE} (feature disabled / unmanaged deployment).
     */
    public record PullSecretPlan(Action action, String secretName, Secret secret) {

        public enum Action {
            PROVISION, CONDEMN, NONE
        }

        public static PullSecretPlan provision(String secretName, Secret secret) {
            return new PullSecretPlan(Action.PROVISION, secretName, secret);
        }

        public static PullSecretPlan condemn() {
            return new PullSecretPlan(Action.CONDEMN, null, null);
        }

        public static PullSecretPlan none() {
            return new PullSecretPlan(Action.NONE, null, null);
        }
    }
}
