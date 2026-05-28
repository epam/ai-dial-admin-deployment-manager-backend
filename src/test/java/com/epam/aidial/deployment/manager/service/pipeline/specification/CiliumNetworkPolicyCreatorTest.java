package com.epam.aidial.deployment.manager.service.pipeline.specification;

import io.cilium.v2.CiliumNetworkPolicy;
import io.cilium.v2.ciliumnetworkpolicyspec.Egress;
import io.cilium.v2.ciliumnetworkpolicyspec.Ingress;
import io.cilium.v2.ciliumnetworkpolicyspec.egress.ToEndpoints;
import io.cilium.v2.ciliumnetworkpolicyspec.ingress.FromEndpoints;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class CiliumNetworkPolicyCreatorTest {

    private static final String NAMESPACE = "kserve-models";
    private static final String LABEL_NAME = "serving.kserve.io/inferenceservice";
    private static final String SERVICE_NAME = "dm-deberta";
    private static final String NS_LABEL = "k8s:io.kubernetes.pod.namespace";

    private final CiliumNetworkPolicyCreator creator = new CiliumNetworkPolicyCreator();

    @Test
    void shouldEmitChainedEgressBlock_whenChainedTrue() {
        CiliumNetworkPolicy policy = creator.create(
                NAMESPACE, LABEL_NAME, SERVICE_NAME, List.of("*"), null, true);

        List<Egress> egress = policy.getSpec().getEgress();
        assertThat(egress).isNotNull();

        // Expected layout: external-world, kube-dns, chained-intra-cluster
        assertThat(egress).hasSize(3);

        Egress chained = egress.get(2);
        assertThat(chained.getToPorts())
                .as("chained egress block has no toPorts constraint (spec 022 R-004)")
                .isNull();

        List<ToEndpoints> toEndpoints = chained.getToEndpoints();
        assertThat(toEndpoints).hasSize(3);

        // Entry 1: same-InferenceService pods (predictor ↔ transformer)
        assertThat(toEndpoints.get(0).getMatchLabels())
                .containsExactlyEntriesOf(Map.of(LABEL_NAME, SERVICE_NAME));

        // Entry 2: istio-system namespace
        assertThat(toEndpoints.get(1).getMatchLabels())
                .containsExactlyEntriesOf(Map.of(NS_LABEL, "istio-system"));

        // Entry 3: knative-serving namespace
        assertThat(toEndpoints.get(2).getMatchLabels())
                .containsExactlyEntriesOf(Map.of(NS_LABEL, "knative-serving"));
    }

    @Test
    void shouldAppendSameInferenceServiceFromEndpoint_whenChainedTrue() {
        CiliumNetworkPolicy policy = creator.create(
                NAMESPACE, LABEL_NAME, SERVICE_NAME, List.of("*"), null, true);

        List<Ingress> ingress = policy.getSpec().getIngress();
        // Rule 0 = fromEndpoints; Rule 1 = toPorts (see createIngress)
        List<FromEndpoints> from = ingress.get(0).getFromEndpoints();

        assertThat(from).hasSize(4);
        // Existing three istio/knative entries are unchanged…
        assertThat(from.get(0).getMatchLabels()).containsEntry("app", "istio-ingressgateway");
        assertThat(from.get(1).getMatchLabels()).containsEntry("app", "activator");
        assertThat(from.get(2).getMatchLabels()).containsEntry("app", "autoscaler");
        // …plus the new same-InferenceService entry
        assertThat(from.get(3).getMatchLabels())
                .containsExactlyEntriesOf(Map.of(LABEL_NAME, SERVICE_NAME));
    }

    @Test
    void shouldAppend8080TcpToIngressPorts_whenChainedTrue() {
        CiliumNetworkPolicy policy = creator.create(
                NAMESPACE, LABEL_NAME, SERVICE_NAME, List.of("*"), null, true);

        var portsList = policy.getSpec().getIngress().get(1).getToPorts().get(0).getPorts();
        var portStrings = portsList.stream().map(p -> p.getPort()).toList();

        assertThat(portStrings).contains("8012", "8022", "8080");
        // exactly one 8080 entry
        assertThat(portStrings.stream().filter("8080"::equals).count()).isEqualTo(1L);
    }

    @Test
    void shouldDeduplicatePort8080_whenAlreadyResolvedFromContainerPort() {
        // Container-port resolution already produced 8080 → chained-mode logic must dedupe.
        CiliumNetworkPolicy policy = creator.create(
                NAMESPACE, LABEL_NAME, SERVICE_NAME, List.of("*"), Set.of(8080), true);

        var portsList = policy.getSpec().getIngress().get(1).getToPorts().get(0).getPorts();
        long count8080 = portsList.stream()
                .filter(p -> "8080".equals(p.getPort()))
                .count();

        assertThat(count8080)
                .as("8080/TCP must appear exactly once after dedup")
                .isEqualTo(1L);
    }

    @Test
    void shouldEmitClusterOnlyKubeDnsEgress_whenAllowedDomainsEmpty_andChainedTrue() {
        // External egress is locked down (allowedDomains=[]), but the chained pair still needs
        // cluster-local DNS to resolve *-predictor.<ns>.svc.cluster.local. Emit a kube-dns block
        // restricted to *.svc.cluster.local — no external DNS exfil.
        CiliumNetworkPolicy policy = creator.create(
                NAMESPACE, LABEL_NAME, SERVICE_NAME, List.of(), null, true);

        List<Egress> egress = policy.getSpec().getEgress();

        // Expected layout: cluster-DNS-only kube-dns, then chained intra-cluster.
        assertThat(egress).hasSize(2);

        // Entry 0: kube-dns endpoint, single matchPattern "*.svc.cluster.local"
        var kubeDns = egress.get(0);
        assertThat(kubeDns.getToEndpoints()).hasSize(1);
        assertThat(kubeDns.getToEndpoints().get(0).getMatchLabels())
                .containsEntry("k8s:k8s-app", "kube-dns");
        var dnsRules = kubeDns.getToPorts().get(0).getRules().getDns();
        assertThat(dnsRules).hasSize(1);
        assertThat(dnsRules.get(0).getMatchPattern()).isEqualTo("*.svc.cluster.local");
        assertThat(dnsRules.get(0).getMatchName()).isNull();

        // Entry 1: chained intra-cluster block (3 toEndpoints)
        assertThat(egress.get(1).getToEndpoints()).hasSize(3);
        assertThat(egress.get(1).getToEndpoints().get(0).getMatchLabels())
                .containsExactlyEntriesOf(Map.of(LABEL_NAME, SERVICE_NAME));
    }

    @Test
    void shouldEmitFullKubeDnsAndChainedBlock_whenAllowedDomainsPresent_andChainedTrue() {
        // When allowedDomains is non-empty, the standard external + full kube-dns blocks emit
        // alongside the chained intra-cluster block — the cluster-DNS-only variant is NOT used.
        CiliumNetworkPolicy policy = creator.create(
                NAMESPACE, LABEL_NAME, SERVICE_NAME, List.of("*"), null, true);

        List<Egress> egress = policy.getSpec().getEgress();
        assertThat(egress).hasSize(3);

        // kube-dns at index 1 carries the wildcard DNS rule, not the cluster-local pattern.
        var dnsRules = egress.get(1).getToPorts().get(0).getRules().getDns();
        assertThat(dnsRules).hasSize(1);
        assertThat(dnsRules.get(0).getMatchPattern()).isEqualTo("*");
    }

    @Test
    void shouldNotEmitChainedAdditions_whenChainedFalse() {
        CiliumNetworkPolicy policy = creator.create(
                NAMESPACE, LABEL_NAME, SERVICE_NAME, List.of("*"), null, false);

        // Egress: world + kube-dns only, no chained block.
        assertThat(policy.getSpec().getEgress()).hasSize(2);

        // Ingress fromEndpoints: 3 entries (istio-ingressgateway, activator, autoscaler).
        assertThat(policy.getSpec().getIngress().get(0).getFromEndpoints()).hasSize(3);

        // Ingress ports: 8012, 8022 only; no 8080.
        var ports = policy.getSpec().getIngress().get(1).getToPorts().get(0).getPorts();
        assertThat(ports.stream().map(p -> p.getPort()).toList())
                .containsExactly("8012", "8022");
    }

    @Test
    void shouldProduceIdenticalPolicyTo5ArgOverload_whenChainedFalse() {
        // SC-002 byte-equivalence canary: the new 6-arg form with chainedTransformer=false
        // must produce a CiliumNetworkPolicy equal to what the existing 5-arg overload produces.
        var fivePolicy = creator.create(
                NAMESPACE, LABEL_NAME, SERVICE_NAME, List.of("example.com"), Set.of(8080));
        var sixPolicy = creator.create(
                NAMESPACE, LABEL_NAME, SERVICE_NAME, List.of("example.com"), Set.of(8080), false);

        assertThat(sixPolicy).usingRecursiveComparison().isEqualTo(fivePolicy);
    }
}
