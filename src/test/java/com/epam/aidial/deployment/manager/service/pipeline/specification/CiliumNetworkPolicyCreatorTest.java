package com.epam.aidial.deployment.manager.service.pipeline.specification;

import io.cilium.v2.CiliumNetworkPolicy;
import io.cilium.v2.ciliumnetworkpolicyspec.Egress;
import io.cilium.v2.ciliumnetworkpolicyspec.Ingress;
import io.cilium.v2.ciliumnetworkpolicyspec.egress.ToEndpoints;
import io.cilium.v2.ciliumnetworkpolicyspec.egress.toports.rules.Dns;
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
                .as("chained egress block has no toPorts constraint (spec 022 FR-002)")
                .isNull();

        List<ToEndpoints> toEndpoints = chained.getToEndpoints();
        // Narrowed selectors: sameInferenceService + istiod + istio-ingressgateway
        //                     + activator + autoscaler + controller
        assertThat(toEndpoints).hasSize(6);

        // Entry 1: same-InferenceService pods (predictor ↔ transformer)
        assertThat(toEndpoints.get(0).getMatchLabels())
                .containsExactlyInAnyOrderEntriesOf(Map.of(LABEL_NAME, SERVICE_NAME));

        // Entry 2: istiod in istio-system
        assertThat(toEndpoints.get(1).getMatchLabels())
                .containsExactlyInAnyOrderEntriesOf(Map.of(NS_LABEL, "istio-system", "app", "istiod"));

        // Entry 3: istio-ingressgateway in istio-system
        assertThat(toEndpoints.get(2).getMatchLabels())
                .containsExactlyInAnyOrderEntriesOf(
                        Map.of(NS_LABEL, "istio-system", "app", "istio-ingressgateway"));

        // Entry 4: knative activator
        assertThat(toEndpoints.get(3).getMatchLabels())
                .containsExactlyInAnyOrderEntriesOf(Map.of(NS_LABEL, "knative-serving", "app", "activator"));

        // Entry 5: knative autoscaler
        assertThat(toEndpoints.get(4).getMatchLabels())
                .containsExactlyInAnyOrderEntriesOf(Map.of(NS_LABEL, "knative-serving", "app", "autoscaler"));

        // Entry 6: knative controller
        assertThat(toEndpoints.get(5).getMatchLabels())
                .containsExactlyInAnyOrderEntriesOf(Map.of(NS_LABEL, "knative-serving", "app", "controller"));
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
    void shouldIncludeCallerPortsInIngressToPorts_whenChainedTrue() {
        // 8080 (KServe model-server port) arrives via `ports` from the caller — production
        // wires this in via getCiliumIngressPorts → DEFAULT_KSERVE_SERVICE_PORT. The chained
        // branch no longer adds 8080 as a literal, so the only entry comes from `ports`.
        CiliumNetworkPolicy policy = creator.create(
                NAMESPACE, LABEL_NAME, SERVICE_NAME, List.of("*"), Set.of(8080), true);

        var portsList = policy.getSpec().getIngress().get(1).getToPorts().get(0).getPorts();
        var portStrings = portsList.stream().map(p -> p.getPort()).toList();

        assertThat(portStrings).contains("8012", "8022", "8080");
        assertThat(portStrings.stream().filter("8080"::equals).count())
                .as("8080/TCP must appear exactly once")
                .isEqualTo(1L);
    }

    @Test
    void shouldNotInject8080AsChainedLiteral_whenCallerDidNotPassIt() {
        // Regression guard for review finding #6: the chained branch no longer injects 8080
        // unconditionally. If the caller doesn't pass it, the policy doesn't expose it.
        CiliumNetworkPolicy policy = creator.create(
                NAMESPACE, LABEL_NAME, SERVICE_NAME, List.of("*"), null, true);

        var portsList = policy.getSpec().getIngress().get(1).getToPorts().get(0).getPorts();
        var portStrings = portsList.stream().map(p -> p.getPort()).toList();

        assertThat(portStrings).containsExactly("8012", "8022");
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

        // Entry 1: chained intra-cluster block (6 toEndpoints with narrowed selectors)
        assertThat(egress.get(1).getToEndpoints()).hasSize(6);
        assertThat(egress.get(1).getToEndpoints().get(0).getMatchLabels())
                .containsExactlyInAnyOrderEntriesOf(Map.of(LABEL_NAME, SERVICE_NAME));
    }

    @Test
    void shouldEmitFullKubeDnsAndChainedBlock_whenAllowedDomainsPresent_andChainedTrue() {
        // When allowedDomains is "*", the wildcard matchPattern already covers cluster-local
        // names — no extra entry needed.
        CiliumNetworkPolicy policy = creator.create(
                NAMESPACE, LABEL_NAME, SERVICE_NAME, List.of("*"), null, true);

        List<Egress> egress = policy.getSpec().getEgress();
        assertThat(egress).hasSize(3);

        var dnsRules = egress.get(1).getToPorts().get(0).getRules().getDns();
        assertThat(dnsRules).hasSize(1);
        assertThat(dnsRules.get(0).getMatchPattern()).isEqualTo("*");
    }

    @Test
    void shouldAppendClusterLocalDnsPattern_whenChainedAndSpecificDomains() {
        // Regression guard for review finding #1: the production HF configuration uses specific
        // non-wildcard domains (huggingface.co, …). Without an explicit cluster-local matchPattern,
        // Cilium's DNS proxy refuses *-predictor.<ns>.svc.cluster.local and the chained pair fails.
        CiliumNetworkPolicy policy = creator.create(
                NAMESPACE, LABEL_NAME, SERVICE_NAME,
                List.of("huggingface.co", "transfer.xethub.hf.co"), null, true);

        List<Egress> egress = policy.getSpec().getEgress();
        // Expected: external-egress + kube-dns + chained intra-cluster
        assertThat(egress).hasSize(3);

        var dnsRules = egress.get(1).getToPorts().get(0).getRules().getDns();
        var matchNames = dnsRules.stream().map(Dns::getMatchName).filter(java.util.Objects::nonNull).toList();
        var matchPatterns = dnsRules.stream().map(Dns::getMatchPattern).filter(java.util.Objects::nonNull).toList();

        assertThat(matchNames).containsExactlyInAnyOrder("huggingface.co", "transfer.xethub.hf.co");
        assertThat(matchPatterns).containsExactly("*.svc.cluster.local");
    }

    @Test
    void shouldNotAppendClusterLocalDnsPattern_whenSpecificDomainsButNotChained() {
        // Predictor-only / non-chained paths must NOT gain the cluster-local pattern — preserves
        // byte-equivalence with the pre-feature policy (SC-002).
        CiliumNetworkPolicy policy = creator.create(
                NAMESPACE, LABEL_NAME, SERVICE_NAME, List.of("huggingface.co"), null, false);

        var dnsRules = policy.getSpec().getEgress().get(1).getToPorts().get(0).getRules().getDns();
        var matchPatterns = dnsRules.stream().map(Dns::getMatchPattern).filter(java.util.Objects::nonNull).toList();

        assertThat(matchPatterns).isEmpty();
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
