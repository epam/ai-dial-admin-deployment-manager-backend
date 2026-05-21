package com.epam.aidial.deployment.manager.kubernetes.hubble;

import com.epam.aidial.deployment.manager.configuration.logging.LogExecution;
import org.springframework.stereotype.Component;

/**
 * Filters DNS query names observed via Hubble Relay, retaining only bare external FQDNs.
 *
 * <p>Kubernetes pods use {@code ndots:5} DNS configuration, which causes the resolver to try
 * appending cluster search domains (e.g., {@code .svc.cluster.local}, {@code .cluster.local},
 * cloud-provider-internal suffix) before falling back to the bare FQDN. These search-domain
 * queries appear as DROPPED flows but do not represent Cilium policy decisions; only the bare
 * FQDN query carries the actual verdict.
 *
 * <p>Filter rule (FR-009): discard any DNS query whose name, after stripping the trailing {@code .},
 * satisfies ANY of the following:
 * <ol>
 *   <li>ends with {@code .cluster.local}</li>
 *   <li>contains {@code .svc.cluster.local}</li>
 *   <li>contains {@code .internal.} (cloud-provider internal suffixes such as
 *       {@code .bx.internal.cloudapp.net})</li>
 * </ol>
 *
 * <p>Do NOT use the looser {@code contains(".cluster.")} check — it would incorrectly filter valid
 * external domains like {@code my-cluster.example.com}.
 */
@Component
@LogExecution
public class HubbleDomainFilter {

    /**
     * Returns {@code true} if the DNS query name represents a bare external FQDN that should be
     * recorded as a domain access entry.
     *
     * @param queryName raw DNS query name, possibly with a trailing {@code .}
     * @return {@code true} if external (should be recorded), {@code false} if cluster-internal noise
     */
    public boolean isExternalDomain(String queryName) {
        if (queryName == null || queryName.isBlank()) {
            return false;
        }
        // Strip trailing dot — Hubble emits FQDNs with a trailing "." (e.g. "auth.docker.io.")
        String name = queryName.endsWith(".") ? queryName.substring(0, queryName.length() - 1) : queryName;

        if (name.endsWith(".cluster.local")) {
            return false;
        }
        if (name.contains(".svc.cluster.local")) {
            return false;
        }
        if (name.contains(".internal.")) {
            return false;
        }
        return true;
    }
}
