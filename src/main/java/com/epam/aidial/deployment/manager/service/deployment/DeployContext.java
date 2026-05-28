package com.epam.aidial.deployment.manager.service.deployment;

/**
 * Carrier for the prepared K8s manifest plus a deploy-time side-channel flag.
 *
 * <p>The {@code chainedTransformer} bit lets the inference path tell the cilium-policy
 * creation site that the generated InferenceService carries a transformer block — without
 * persisting the fact, fetching it again from HuggingFace, or re-parsing the partial
 * manifest (spec 022 FR-005 / research R-001).
 */
public record DeployContext<S>(S spec, boolean chainedTransformer) {

    public static <S> DeployContext<S> unchained(S spec) {
        return new DeployContext<>(spec, false);
    }

    public static <S> DeployContext<S> chained(S spec) {
        return new DeployContext<>(spec, true);
    }
}
