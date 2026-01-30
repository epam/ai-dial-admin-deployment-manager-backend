package com.epam.aidial.deployment.manager.service.pipeline.specification;

import com.epam.aidial.deployment.manager.configuration.logging.LogExecution;
import com.epam.aidial.deployment.manager.utils.K8sNamingUtils;
import io.cilium.v2.CiliumNetworkPolicy;
import io.cilium.v2.CiliumNetworkPolicySpec;
import io.cilium.v2.ciliumnetworkpolicyspec.Egress;
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
    private static final String KUBE_DNS_LABEL_NAME = "k8s:k8s-app";
    private static final String KUBE_DNS_LABEL_VALUE = "kube-dns";
    private static final String KUBE_DNS_NAMESPACE_LABEL_NAME = "k8s:io.kubernetes.pod.namespace";
    private static final String KUBE_DNS_NAMESPACE_LABEL_VALUE = "kube-system";
    private static final String APP = "app";

    @Value("${app.cilium-network-policies-enabled}")
    private boolean ciliumNetworkPoliciesEnabled;

    public CiliumNetworkPolicy create(String namespace, String matchLabelName, String matchLabelValue, List<String> allowedDomains) {
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
        if (CollectionUtils.isNotEmpty(allowedDomains)) {
            egressList.add(createFqdnEgress(allowedDomains));
        }
        egressList.add(createKubeDnsEgress(allowedDomains));
        spec.setEgress(egressList);
        spec.setIngress(createIngress());

        // CiliumNetworkPolicy
        CiliumNetworkPolicy policy = new CiliumNetworkPolicy();
        policy.setMetadata(metadata);
        policy.setSpec(spec);

        return policy;
    }

    private Egress createFqdnEgress(List<String> allowedDomains) {
        List<ToFQDNs> toFqdnsList = allowedDomains.stream()
                .map(domain -> {
                    ToFQDNs toFqdns = new ToFQDNs();
                    toFqdns.setMatchName(domain);
                    return toFqdns;
                })
                .toList();

        Ports tcpPort443 = new Ports();
        tcpPort443.setPort(TCP_PORT_443);
        tcpPort443.setProtocol(Protocol.TCP);

        // Port 80 is required for package downloads in some images (for example, debian-based)
        Ports tcpPort80 = new Ports();
        tcpPort80.setPort(TCP_PORT_80);
        tcpPort80.setProtocol(Protocol.TCP);

        ToPorts tcpToPorts = new ToPorts();
        tcpToPorts.setPorts(List.of(tcpPort443, tcpPort80));
        tcpToPorts.setServerNames(allowedDomains);

        Egress egress = new Egress();
        egress.setToFQDNs(toFqdnsList);
        egress.setToPorts(List.of(tcpToPorts));
        return egress;
    }

    private Egress createKubeDnsEgress(List<String> allowedDomains) {
        ToEndpoints kubeDnsToEndpoints = new ToEndpoints();
        kubeDnsToEndpoints.setMatchLabels(Map.of(
                KUBE_DNS_LABEL_NAME, KUBE_DNS_LABEL_VALUE,
                KUBE_DNS_NAMESPACE_LABEL_NAME, KUBE_DNS_NAMESPACE_LABEL_VALUE
        ));

        Ports kubeDnsPorts = new Ports();
        kubeDnsPorts.setPort(UDP_PORT);
        kubeDnsPorts.setProtocol(Protocol.ANY);

        List<Dns> dnsList = allowedDomains.stream()
                .map(domain -> {
                    Dns dns = new Dns();
                    dns.setMatchName(domain);
                    return dns;
                })
                .toList();

        Rules rules = new Rules();
        rules.setDns(dnsList);

        ToPorts kubeDnsToPorts = new ToPorts();
        kubeDnsToPorts.setPorts(List.of(kubeDnsPorts));
        kubeDnsToPorts.setRules(rules);

        Egress egress = new Egress();
        egress.setToEndpoints(List.of(kubeDnsToEndpoints));
        egress.setToPorts(List.of(kubeDnsToPorts));
        return egress;
    }

    private List<Ingress> createIngress() {
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

        io.cilium.v2.ciliumnetworkpolicyspec.ingress.ToPorts ingressToPorts =
                new io.cilium.v2.ciliumnetworkpolicyspec.ingress.ToPorts();
        ingressToPorts.setPorts(List.of(port8012, port8022));

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
