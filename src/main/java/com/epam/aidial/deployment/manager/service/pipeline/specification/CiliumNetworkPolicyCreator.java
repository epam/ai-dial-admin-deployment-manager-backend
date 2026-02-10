package com.epam.aidial.deployment.manager.service.pipeline.specification;

import com.epam.aidial.deployment.manager.configuration.logging.LogExecution;
import com.epam.aidial.deployment.manager.utils.K8sNamingUtils;
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
    private static final String KUBE_DNS_LABEL_NAME = "k8s:k8s-app";
    private static final String KUBE_DNS_LABEL_VALUE = "kube-dns";
    private static final String KUBE_DNS_NAMESPACE_LABEL_NAME = "k8s:io.kubernetes.pod.namespace";
    private static final String KUBE_DNS_NAMESPACE_LABEL_VALUE = "kube-system";
    private static final String ALLOW_ALL_KEY = "*";
    private static final String APP = "app";

    @Value("${app.cilium-network-policies-enabled}")
    private boolean ciliumNetworkPoliciesEnabled;

    public CiliumNetworkPolicy create(@NotNull String namespace,
                                      @NotNull String matchLabelName,
                                      @NotNull String matchLabelValue,
                                      @NotNull List<String> allowedDomains,
                                      @Nullable Integer containerPort) {
        List<String> domains = new ArrayList<>(allowedDomains);

        // Detect if all egress to FQDNs should be allowed
        boolean shouldAllowAllEgressToFqdns = false;
        if (CollectionUtils.isNotEmpty(domains) && domains.contains(ALLOW_ALL_KEY)) {
            domains = new ArrayList<>();
            shouldAllowAllEgressToFqdns = true;
        }

        // Metadata
        ObjectMeta metadata = new ObjectMeta();
        metadata.setName(getPolicyName(matchLabelValue));
        metadata.setNamespace(namespace);

        // EndpointSelector
        EndpointSelector endpointSelector = new EndpointSelector();
        endpointSelector.setMatchLabels(Map.of(matchLabelName, matchLabelValue));

        // CiliumNetworkPolicySpec
        CiliumNetworkPolicySpec spec = new CiliumNetworkPolicySpec();
        spec.setEndpointSelector(endpointSelector);
        List<Egress> egressList = new ArrayList<>();
        if (CollectionUtils.isNotEmpty(domains) || shouldAllowAllEgressToFqdns) {
            egressList.add(createExternalEgress(domains));
        }
        egressList.add(createKubeDnsEgress());
        spec.setEgress(egressList);
        spec.setIngress(createIngress(containerPort));

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

        if (CollectionUtils.isEmpty(domains)) {
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

    private Egress createKubeDnsEgress() {
        ToEndpoints kubeDnsToEndpoints = new ToEndpoints();
        kubeDnsToEndpoints.setMatchLabels(Map.of(
                KUBE_DNS_LABEL_NAME, KUBE_DNS_LABEL_VALUE,
                KUBE_DNS_NAMESPACE_LABEL_NAME, KUBE_DNS_NAMESPACE_LABEL_VALUE
        ));

        Ports kubeDnsPorts = new Ports();
        kubeDnsPorts.setPort(UDP_PORT);
        kubeDnsPorts.setProtocol(Protocol.ANY);

        Dns dns = new Dns();
        dns.setMatchPattern(UDP_DNS_PATTERN_ALL);

        Rules rules = new Rules();
        rules.setDns(List.of(dns));

        ToPorts kubeDnsToPorts = new ToPorts();
        kubeDnsToPorts.setPorts(List.of(kubeDnsPorts));
        kubeDnsToPorts.setRules(rules);

        Egress egress = new Egress();
        egress.setToEndpoints(List.of(kubeDnsToEndpoints));
        egress.setToPorts(List.of(kubeDnsToPorts));
        return egress;
    }

    private List<Ingress> createIngress(@Nullable Integer containerPort) {
        FromEndpoints fromEndpoints = new FromEndpoints();
        fromEndpoints.setMatchLabels(Map.of(
                KUBE_DNS_NAMESPACE_LABEL_NAME, "istio-system", APP, "istio-ingressgateway"
        ));

        FromEndpoints fromEndpointsActivator = new FromEndpoints();
        fromEndpointsActivator.setMatchLabels(Map.of(
                KUBE_DNS_NAMESPACE_LABEL_NAME, "knative-serving", APP, "activator"
        ));

        FromEndpoints fromEndpointsAutoscaler = new FromEndpoints();
        fromEndpointsAutoscaler.setMatchLabels(Map.of(
                KUBE_DNS_NAMESPACE_LABEL_NAME, "knative-serving", APP, "autoscaler"
        ));

        // Specifying full path to avoid conflicts with similar classes in 'egress' package
        io.cilium.v2.ciliumnetworkpolicyspec.ingress.toports.Ports port8012 =
                new io.cilium.v2.ciliumnetworkpolicyspec.ingress.toports.Ports();
        port8012.setPort(INGRESS_PORT_8012);
        port8012.setProtocol(io.cilium.v2.ciliumnetworkpolicyspec.ingress.toports.Ports.Protocol.TCP);

        io.cilium.v2.ciliumnetworkpolicyspec.ingress.toports.Ports port8022 =
                new io.cilium.v2.ciliumnetworkpolicyspec.ingress.toports.Ports();
        port8022.setPort(INGRESS_PORT_8022);
        port8022.setProtocol(io.cilium.v2.ciliumnetworkpolicyspec.ingress.toports.Ports.Protocol.TCP);

        List<io.cilium.v2.ciliumnetworkpolicyspec.ingress.toports.Ports> ports = new ArrayList<>();
        ports.add(port8012);
        ports.add(port8022);

        if (containerPort != null) {
            io.cilium.v2.ciliumnetworkpolicyspec.ingress.toports.Ports port =
                    new io.cilium.v2.ciliumnetworkpolicyspec.ingress.toports.Ports();
            port.setPort(containerPort.toString());
            port.setProtocol(io.cilium.v2.ciliumnetworkpolicyspec.ingress.toports.Ports.Protocol.TCP);
            ports.add(port);
        }

        io.cilium.v2.ciliumnetworkpolicyspec.ingress.ToPorts ingressToPorts =
                new io.cilium.v2.ciliumnetworkpolicyspec.ingress.ToPorts();
        ingressToPorts.setPorts(ports);

        Ingress fromEndpointsIngressRule = new Ingress();
        fromEndpointsIngressRule.setFromEndpoints(List.of(fromEndpoints, fromEndpointsActivator, fromEndpointsAutoscaler));

        Ingress toPortsIngressRule = new Ingress();
        toPortsIngressRule.setToPorts(List.of(ingressToPorts));

        return List.of(fromEndpointsIngressRule, toPortsIngressRule);
    }

    public static String getPolicyName(String matchLabelValue) {
        return K8sNamingUtils.generateName(matchLabelValue);
    }
}
