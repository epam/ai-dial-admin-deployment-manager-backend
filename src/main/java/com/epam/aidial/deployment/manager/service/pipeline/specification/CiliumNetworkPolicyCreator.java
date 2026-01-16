package com.epam.aidial.deployment.manager.service.pipeline.specification;

import com.epam.aidial.deployment.manager.configuration.logging.LogExecution;
import io.cilium.v2.CiliumNetworkPolicy;
import io.cilium.v2.CiliumNetworkPolicySpec;
import io.cilium.v2.ciliumnetworkpolicyspec.Egress;
import io.cilium.v2.ciliumnetworkpolicyspec.EndpointSelector;
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

    private static final String POLICY_NAME_TEMPLATE = "restrict-egress-%s";
    private static final String UDP_DNS_PATTERN_ALL = "*";
    private static final String TCP_PORT = "443";
    private static final String UDP_PORT = "53";

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

        // Egress to allowed FQDNs
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

        Egress egressFqdn = new Egress();
        egressFqdn.setToFQDNs(toFqdnsList);
        egressFqdn.setToPorts(List.of(tcpToPorts));

        // Egress for DNS
        Ports udpPorts = new Ports();
        udpPorts.setPort(UDP_PORT);
        udpPorts.setProtocol(Protocol.UDP);

        Dns dns = new Dns();
        dns.setMatchPattern(UDP_DNS_PATTERN_ALL);

        Rules rules = new Rules();
        rules.setDns(List.of(dns));

        ToPorts udpToPorts = new ToPorts();
        udpToPorts.setPorts(List.of(udpPorts));
        udpToPorts.setRules(rules);

        Egress egressDns = new Egress();
        egressDns.setToPorts(List.of(udpToPorts));

        // CiliumNetworkPolicySpec
        CiliumNetworkPolicySpec spec = new CiliumNetworkPolicySpec();
        spec.setEndpointSelector(endpointSelector);
        spec.setEgress(List.of(egressFqdn, egressDns));

        // CiliumNetworkPolicy
        CiliumNetworkPolicy policy = new CiliumNetworkPolicy();
        policy.setMetadata(metadata);
        policy.setSpec(spec);

        return policy;
    }

    public static String getPolicyName(String matchLabelValue) {
        return POLICY_NAME_TEMPLATE.formatted(matchLabelValue);
    }
}
