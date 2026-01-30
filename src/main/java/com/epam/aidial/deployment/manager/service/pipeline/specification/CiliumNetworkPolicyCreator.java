package com.epam.aidial.deployment.manager.service.pipeline.specification;

import com.epam.aidial.deployment.manager.configuration.logging.LogExecution;
import com.epam.aidial.deployment.manager.utils.K8sNamingUtils;
import io.cilium.v2.CiliumNetworkPolicy;
import io.cilium.v2.CiliumNetworkPolicySpec;
import io.cilium.v2.ciliumnetworkpolicyspec.Egress;
import io.cilium.v2.ciliumnetworkpolicyspec.EndpointSelector;
import io.cilium.v2.ciliumnetworkpolicyspec.egress.ToEndpoints;
import io.cilium.v2.ciliumnetworkpolicyspec.egress.ToFQDNs;
import io.cilium.v2.ciliumnetworkpolicyspec.egress.ToPorts;
import io.cilium.v2.ciliumnetworkpolicyspec.egress.toports.Ports;
import io.cilium.v2.ciliumnetworkpolicyspec.egress.toports.Ports.Protocol;
import io.cilium.v2.ciliumnetworkpolicyspec.egress.toports.Rules;
import io.cilium.v2.ciliumnetworkpolicyspec.egress.toports.rules.Dns;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Getter
@Component
@LogExecution
public class CiliumNetworkPolicyCreator {

    private static final String TCP_PORT = "443";
    private static final String UDP_PORT = "53";
    private static final String KUBE_DNS_LABEL_NAME = "k8s:k8s-app";
    private static final String KUBE_DNS_LABEL_VALUE = "kube-dns";
    private static final String KUBE_DNS_NAMESPACE_LABEL_NAME = "k8s:io.kubernetes.pod.namespace";
    private static final String KUBE_DNS_NAMESPACE_LABEL_VALUE = "kube-system";

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
        spec.setEgress(List.of(createFqdnEgress(allowedDomains), createKubeDnsEgress(allowedDomains)));

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

        Ports tcpPorts = new Ports();
        tcpPorts.setPort(TCP_PORT);
        tcpPorts.setProtocol(Protocol.TCP);

        ToPorts tcpToPorts = new ToPorts();
        tcpToPorts.setPorts(List.of(tcpPorts));
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

    public static String getPolicyName(String matchLabelValue) {
        return K8sNamingUtils.generateName(matchLabelValue);
    }
}
