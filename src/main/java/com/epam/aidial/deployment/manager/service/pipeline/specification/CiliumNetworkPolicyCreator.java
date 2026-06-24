package com.epam.aidial.deployment.manager.service.pipeline.specification;

import com.epam.aidial.deployment.manager.configuration.logging.LogExecution;
import io.cilium.v2.CiliumNetworkPolicy;
import io.cilium.v2.CiliumNetworkPolicySpec;
import io.cilium.v2.ciliumnetworkpolicyspec.Egress;
import io.cilium.v2.ciliumnetworkpolicyspec.Egress.ToEntities;
import io.cilium.v2.ciliumnetworkpolicyspec.EndpointSelector;
import io.cilium.v2.ciliumnetworkpolicyspec.Ingress;
import io.cilium.v2.ciliumnetworkpolicyspec.egress.ToEndpoints;
import io.cilium.v2.ciliumnetworkpolicyspec.egress.ToFQDNs;
import io.cilium.v2.ciliumnetworkpolicyspec.egress.ToPorts;
import io.cilium.v2.ciliumnetworkpolicyspec.egress.toports.Ports;
import io.cilium.v2.ciliumnetworkpolicyspec.egress.toports.Ports.Protocol;
import io.cilium.v2.ciliumnetworkpolicyspec.egress.toports.Rules;
import io.cilium.v2.ciliumnetworkpolicyspec.egress.toports.rules.Dns;
import io.cilium.v2.ciliumnetworkpolicyspec.ingress.FromEndpoints;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import lombok.Getter;
import org.apache.commons.collections4.CollectionUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Getter
@Component
@LogExecution
public class CiliumNetworkPolicyCreator {

    private static final String TCP_PORT_80 = "80";
    private static final String TCP_PORT_443 = "443";
    private static final String UDP_PORT = "53";
    private static final String INGRESS_PORT_8012 = "8012";
    private static final String INGRESS_PORT_8022 = "8022";
    private static final String UDP_DNS_PATTERN_ALL = "*";
    private static final String CLUSTER_LOCAL_DNS_PATTERN = "*.%s.svc.cluster.local";
    private static final String KUBE_DNS_LABEL_NAME = "k8s:k8s-app";
    private static final String KUBE_DNS_LABEL_VALUE = "kube-dns";
    private static final String KUBE_DNS_NAMESPACE_LABEL_NAME = "k8s:io.kubernetes.pod.namespace";
    private static final String KUBE_DNS_NAMESPACE_LABEL_VALUE = "kube-system";
    private static final String ISTIO_NAMESPACE_LABEL_VALUE = "istio-system";
    private static final String KNATIVE_NAMESPACE_LABEL_VALUE = "knative-serving";
    private static final String ALLOW_ALL_KEY = "*";
    private static final String APP = "app";
    private static final String ISTIO_INGRESSGATEWAY_APP = "istio-ingressgateway";
    private static final String ISTIOD_APP = "istiod";
    private static final String KNATIVE_ACTIVATOR_APP = "activator";
    private static final String KNATIVE_AUTOSCALER_APP = "autoscaler";
    private static final String KNATIVE_CONTROLLER_APP = "controller";

    @Value("${app.cilium-network-policies-enabled}")
    private boolean ciliumNetworkPoliciesEnabled;

    public CiliumNetworkPolicy create(@NotNull String namespace,
                                      @NotNull String matchLabelName,
                                      @NotNull String matchLabelValue,
                                      @NotNull List<String> allowedDomains,
                                      @Nullable Set<Integer> ports) {
        return create(namespace, matchLabelName, matchLabelValue, allowedDomains, ports, false);
    }

    public CiliumNetworkPolicy create(@NotNull String namespace,
                                      @NotNull String matchLabelName,
                                      @NotNull String matchLabelValue,
                                      @NotNull List<String> allowedDomains,
                                      @Nullable Set<Integer> ports,
                                      boolean chainedTransformer) {
        // Metadata
        ObjectMeta metadata = new ObjectMeta();
        metadata.setName(matchLabelValue); // policy name re-uses service name
        metadata.setNamespace(namespace);

        // EndpointSelector
        EndpointSelector endpointSelector = new EndpointSelector();
        endpointSelector.setMatchLabels(Map.of(matchLabelName, matchLabelValue));

        // CiliumNetworkPolicySpec
        CiliumNetworkPolicySpec spec = new CiliumNetworkPolicySpec();
        spec.setEndpointSelector(endpointSelector);
        List<Egress> egressList = new ArrayList<>();
        boolean hasAllowedDomains = CollectionUtils.isNotEmpty(allowedDomains);
        if (hasAllowedDomains) {
            egressList.add(createExternalEgress(allowedDomains));
            // Chained pairs need to resolve <svc>-predictor.<namespace>.svc.cluster.local via
            // kube-dns; Cilium's DNS proxy would otherwise reject cluster-local names not in
            // the matchName set. Scoped to the deployment's own namespace.
            egressList.add(createKubeDnsEgress(namespace, allowedDomains, chainedTransformer));
        } else if (chainedTransformer) {
            // External egress locked down; emit a kube-dns block restricted to the deployment's
            // own namespace so the predictor's cluster DNS name resolves without DNS exfil.
            egressList.add(createKubeDnsEgress(namespace, List.of(), true));
        }
        if (chainedTransformer) {
            // Intra-cluster reachability for chained predictor + transformer (spec 022 FR-001).
            egressList.add(createChainedIntraClusterEgress(matchLabelName, matchLabelValue));
        }
        if (!egressList.isEmpty()) {
            spec.setEgress(egressList);
        }
        spec.setIngress(createIngress(ports, chainedTransformer, matchLabelName, matchLabelValue));

        // CiliumNetworkPolicy
        CiliumNetworkPolicy policy = new CiliumNetworkPolicy();
        policy.setMetadata(metadata);
        policy.setSpec(spec);

        return policy;
    }

    private Egress createExternalEgress(List<String> domains) {
        Egress egress = new Egress();

        ToPorts tcpToPorts = createToPortsEgress();
        egress.setToPorts(List.of(tcpToPorts));

        if (allowAllEgress(domains)) {
            egress.setToEntities(List.of(ToEntities.WORLD));
        } else {
            List<ToFQDNs> toFqdnsList = domains.stream()
                    .map(domain -> {
                        ToFQDNs toFqdns = new ToFQDNs();
                        toFqdns.setMatchName(domain);
                        return toFqdns;
                    })
                    .toList();
            egress.setToFQDNs(toFqdnsList);
        }
        return egress;
    }

    private ToPorts createToPortsEgress() {
        Ports tcpPort443 = new Ports();
        tcpPort443.setPort(TCP_PORT_443);
        tcpPort443.setProtocol(Protocol.TCP);

        // Port 80 is required for package downloads in some images (for example, debian-based)
        Ports tcpPort80 = new Ports();
        tcpPort80.setPort(TCP_PORT_80);
        tcpPort80.setProtocol(Protocol.TCP);

        ToPorts tcpToPorts = new ToPorts();
        tcpToPorts.setPorts(List.of(tcpPort443, tcpPort80));

        return tcpToPorts;
    }

    private boolean allowAllEgress(List<String> domains) {
        return domains.contains(ALLOW_ALL_KEY);
    }

    private Egress createKubeDnsEgress(String namespace, List<String> domains, boolean includeClusterLocal) {
        ToEndpoints kubeDnsToEndpoints = new ToEndpoints();
        kubeDnsToEndpoints.setMatchLabels(Map.of(
                KUBE_DNS_LABEL_NAME, KUBE_DNS_LABEL_VALUE,
                KUBE_DNS_NAMESPACE_LABEL_NAME, KUBE_DNS_NAMESPACE_LABEL_VALUE
        ));

        Ports kubeDnsPorts = new Ports();
        kubeDnsPorts.setPort(UDP_PORT);
        kubeDnsPorts.setProtocol(Protocol.ANY);
        Rules rules = new Rules();
        rules.setDns(toDnsList(namespace, domains, includeClusterLocal));

        ToPorts kubeDnsToPorts = new ToPorts();
        kubeDnsToPorts.setPorts(List.of(kubeDnsPorts));
        kubeDnsToPorts.setRules(rules);

        Egress egress = new Egress();
        egress.setToEndpoints(List.of(kubeDnsToEndpoints));
        egress.setToPorts(List.of(kubeDnsToPorts));
        return egress;
    }

    private List<Dns> toDnsList(String namespace, List<String> domains, boolean includeClusterLocal) {
        // matchPattern "*" already covers every name including cluster-local — no extra entry.
        if (allowAllEgress(domains)) {
            Dns dns = new Dns();
            dns.setMatchPattern(UDP_DNS_PATTERN_ALL);
            return List.of(dns);
        }
        List<Dns> rules = new ArrayList<>(domains.size() + 1);
        for (String domain : domains) {
            Dns dns = new Dns();
            dns.setMatchName(domain);
            rules.add(dns);
        }
        if (includeClusterLocal) {
            Dns clusterLocal = new Dns();
            clusterLocal.setMatchPattern(CLUSTER_LOCAL_DNS_PATTERN.formatted(namespace));
            rules.add(clusterLocal);
        }
        return rules;
    }

    private Egress createChainedIntraClusterEgress(String matchLabelName, String matchLabelValue) {
        // Narrowed allow-list for the chained data path: same-InferenceService hop + the six
        // specific istio-system / knative-serving control-plane components the predictor +
        // transformer pair must reach. istio-ingressgateway and the Knative controller appear
        // as egress targets per the reference YAML for spec 022.
        ToEndpoints sameInferenceService = new ToEndpoints();
        sameInferenceService.setMatchLabels(Map.of(matchLabelName, matchLabelValue));

        ToEndpoints istiod = new ToEndpoints();
        istiod.setMatchLabels(Map.of(
                KUBE_DNS_NAMESPACE_LABEL_NAME, ISTIO_NAMESPACE_LABEL_VALUE,
                APP, ISTIOD_APP
        ));

        ToEndpoints istioIngressGateway = new ToEndpoints();
        istioIngressGateway.setMatchLabels(Map.of(
                KUBE_DNS_NAMESPACE_LABEL_NAME, ISTIO_NAMESPACE_LABEL_VALUE,
                APP, ISTIO_INGRESSGATEWAY_APP
        ));

        ToEndpoints activator = new ToEndpoints();
        activator.setMatchLabels(Map.of(
                KUBE_DNS_NAMESPACE_LABEL_NAME, KNATIVE_NAMESPACE_LABEL_VALUE,
                APP, KNATIVE_ACTIVATOR_APP
        ));

        ToEndpoints autoscaler = new ToEndpoints();
        autoscaler.setMatchLabels(Map.of(
                KUBE_DNS_NAMESPACE_LABEL_NAME, KNATIVE_NAMESPACE_LABEL_VALUE,
                APP, KNATIVE_AUTOSCALER_APP
        ));

        ToEndpoints knativeController = new ToEndpoints();
        knativeController.setMatchLabels(Map.of(
                KUBE_DNS_NAMESPACE_LABEL_NAME, KNATIVE_NAMESPACE_LABEL_VALUE,
                APP, KNATIVE_CONTROLLER_APP
        ));

        Egress egress = new Egress();
        egress.setToEndpoints(List.of(
                sameInferenceService, istiod, istioIngressGateway, activator, autoscaler, knativeController));
        // No toPorts — intra-cluster control-plane traffic is unrestricted on the port axis (spec 022 FR-002).
        return egress;
    }

    private List<Ingress> createIngress(@Nullable Set<Integer> ports,
                                        boolean chainedTransformer,
                                        String matchLabelName,
                                        String matchLabelValue) {
        FromEndpoints fromEndpoints = new FromEndpoints();
        fromEndpoints.setMatchLabels(Map.of(
                KUBE_DNS_NAMESPACE_LABEL_NAME, ISTIO_NAMESPACE_LABEL_VALUE, APP, ISTIO_INGRESSGATEWAY_APP
        ));

        FromEndpoints fromEndpointsActivator = new FromEndpoints();
        fromEndpointsActivator.setMatchLabels(Map.of(
                KUBE_DNS_NAMESPACE_LABEL_NAME, KNATIVE_NAMESPACE_LABEL_VALUE, APP, KNATIVE_ACTIVATOR_APP
        ));

        FromEndpoints fromEndpointsAutoscaler = new FromEndpoints();
        fromEndpointsAutoscaler.setMatchLabels(Map.of(
                KUBE_DNS_NAMESPACE_LABEL_NAME, KNATIVE_NAMESPACE_LABEL_VALUE, APP, KNATIVE_AUTOSCALER_APP
        ));

        List<FromEndpoints> fromEndpointsList = new ArrayList<>();
        fromEndpointsList.add(fromEndpoints);
        fromEndpointsList.add(fromEndpointsActivator);
        fromEndpointsList.add(fromEndpointsAutoscaler);
        if (chainedTransformer) {
            // Same-InferenceService ingress lets the chained pair accept traffic across components (spec 022 FR-001).
            FromEndpoints sameInferenceService = new FromEndpoints();
            sameInferenceService.setMatchLabels(Map.of(matchLabelName, matchLabelValue));
            fromEndpointsList.add(sameInferenceService);
        }

        // Specifying full path to avoid conflicts with similar classes in 'egress' package
        io.cilium.v2.ciliumnetworkpolicyspec.ingress.toports.Ports port8012 =
                new io.cilium.v2.ciliumnetworkpolicyspec.ingress.toports.Ports();
        port8012.setPort(INGRESS_PORT_8012);
        port8012.setProtocol(io.cilium.v2.ciliumnetworkpolicyspec.ingress.toports.Ports.Protocol.TCP);

        io.cilium.v2.ciliumnetworkpolicyspec.ingress.toports.Ports port8022 =
                new io.cilium.v2.ciliumnetworkpolicyspec.ingress.toports.Ports();
        port8022.setPort(INGRESS_PORT_8022);
        port8022.setProtocol(io.cilium.v2.ciliumnetworkpolicyspec.ingress.toports.Ports.Protocol.TCP);

        List<io.cilium.v2.ciliumnetworkpolicyspec.ingress.toports.Ports> ingressPorts = new ArrayList<>();
        ingressPorts.add(port8012);
        ingressPorts.add(port8022);

        if (CollectionUtils.isNotEmpty(ports)) {
            ports.stream()
                    .filter(Objects::nonNull)
                    .map(port -> {
                        io.cilium.v2.ciliumnetworkpolicyspec.ingress.toports.Ports ingressPort =
                                new io.cilium.v2.ciliumnetworkpolicyspec.ingress.toports.Ports();
                        ingressPort.setPort(port.toString());
                        ingressPort.setProtocol(io.cilium.v2.ciliumnetworkpolicyspec.ingress.toports.Ports.Protocol.TCP);
                        return ingressPort;
                    })
                    .forEach(ingressPorts::add);
        }

        // KServe model-server port 8080 arrives via `ports` from the caller (DEFAULT_KSERVE_SERVICE_PORT).

        io.cilium.v2.ciliumnetworkpolicyspec.ingress.ToPorts ingressToPorts =
                new io.cilium.v2.ciliumnetworkpolicyspec.ingress.ToPorts();
        ingressToPorts.setPorts(ingressPorts);

        Ingress fromEndpointsIngressRule = new Ingress();
        fromEndpointsIngressRule.setFromEndpoints(fromEndpointsList);

        Ingress toPortsIngressRule = new Ingress();
        toPortsIngressRule.setToPorts(List.of(ingressToPorts));

        return List.of(fromEndpointsIngressRule, toPortsIngressRule);
    }
}
