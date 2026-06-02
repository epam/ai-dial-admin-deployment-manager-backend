package com.epam.aidial.deployment.manager.service.pipeline.specification;

import io.cilium.v2.CiliumNetworkPolicy;
import io.fabric8.kubernetes.client.utils.Serialization;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

/**
 * Manual YAML dump utility — NOT a regression test. Run on demand to inspect the rendered
 * CiliumNetworkPolicy for the `deberta` deployment (chained predictor + transformer,
 * allowedDomains=["*"], minReplicas=maxReplicas=1) without having to round-trip through
 * a real cluster.
 *
 * <p>Invocation:
 *   ./gradlew testFast --tests "*CnpYamlDumpTest" -i
 *
 * <p>Output: build/tmp/dm-deberta-cnp.yaml (path also printed to stdout).
 */
class CnpYamlDumpTest {

    @Test
    void dumpDebertaCnp() throws Exception {
        // Inputs mirror what InferenceDeploymentManager would pass for this deployment:
        //   - namespace from the URL `dm-deberta-transformer.kserve-models....`
        //   - matchLabelName is the KServe convention `serving.kserve.io/inferenceservice`
        //   - serviceName is `dm-{deploymentId}` after K8sNamingUtils.generateName(...)
        //   - allowedDomains: ["*"] short-circuits allowAllEgress → wildcard matchPattern
        //     (in production the HF defaults are unioned in but `*` already covers them)
        //   - ports: {8080} comes from getCiliumIngressPorts → DEFAULT_KSERVE_SERVICE_PORT
        //   - chainedTransformer: true (protectai/deberta-v3-base-prompt-injection is a
        //     text-classification model, spec 021 emits a transformer block)
        String namespace = "kserve-models";
        String matchLabelName = "serving.kserve.io/inferenceservice";
        String serviceName = "dm-deberta";
        List<String> allowedDomains = List.of("*");
        Set<Integer> ports = Set.of(8080);
        boolean chainedTransformer = true;

        CiliumNetworkPolicy policy = new CiliumNetworkPolicyCreator().create(
                namespace, matchLabelName, serviceName, allowedDomains, ports, chainedTransformer);

        String yaml = Serialization.asYaml(policy);

        Path out = Path.of("build", "tmp", "dm-deberta-cnp.yaml");
        Files.createDirectories(out.getParent());
        Files.writeString(out, yaml);

        System.out.println("Rendered CiliumNetworkPolicy YAML written to: " + out.toAbsolutePath());
        System.out.println("----- BEGIN YAML -----");
        System.out.println(yaml);
        System.out.println("------ END YAML ------");
    }
}
