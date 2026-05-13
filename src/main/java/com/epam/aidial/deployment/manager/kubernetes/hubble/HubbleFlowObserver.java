package com.epam.aidial.deployment.manager.kubernetes.hubble;

import com.epam.aidial.deployment.manager.configuration.logging.LogExecution;
import com.epam.aidial.deployment.manager.model.CiliumVerdict;
import com.epam.aidial.deployment.manager.model.DomainEntry;
import io.cilium.api.flow.FlowType;
import io.cilium.api.flow.Layer7;
import io.cilium.api.flow.Verdict;
import io.cilium.api.observer.FlowFilter;
import io.cilium.api.observer.GetFlowsRequest;
import io.cilium.api.observer.ObserverGrpc;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.function.Consumer;

/**
 * Streams DNS proxy flows from Hubble Relay for a specific pod namespace and label selector,
 * maps qualifying flows to {@link DomainEntry} records, and delivers them via a callback.
 *
 * <p>Filtering by <em>namespace + label selector</em> rather than pod name: a pod may not yet
 * exist when observation starts, and labels are stable across pod restarts.
 *
 * <p>Observation runs <em>blocking</em> on the calling thread. The caller is responsible for
 * running this on a virtual thread (see {@code HubbleDomainFlowService}). The observation loop
 * terminates when the gRPC stream ends or the thread is interrupted. This class obtains the
 * shared channel from {@link HubbleRelayGrpcChannelFactory} and does not own its lifecycle —
 * the channel must not be shut down here.
 *
 * <p>DNS verdict mapping:
 * <ul>
 *   <li>{@code FORWARDED} → {@link CiliumVerdict#ALLOWED}</li>
 *   <li>{@code DROPPED} → {@link CiliumVerdict#BLOCKED}</li>
 *   <li>All other verdicts are ignored.</li>
 * </ul>
 */
@Slf4j
@Component
@LogExecution
@RequiredArgsConstructor
public class HubbleFlowObserver {

    private final HubbleRelayGrpcChannelFactory channelFactory;
    private final HubbleDomainFilter domainFilter;

    /**
     * Opens a gRPC channel to Hubble Relay, subscribes to L7 DNS flows from pods matching
     * {@code podNamespace} and {@code podLabelSelector}, and invokes {@code onEntry} for each
     * qualifying external-domain flow.
     *
     * <p>Blocks until the stream ends or the calling thread is interrupted.
     *
     * @param podNamespace     Kubernetes namespace to scope the flow filter
     * @param podLabelSelector label selector (e.g. {@code "job-name=dm-abc123"})
     * @param onEntry          callback invoked for each qualifying {@link DomainEntry}
     */
    public void observe(String podNamespace, String podLabelSelector, Consumer<DomainEntry> onEntry) {
        var channel = channelFactory.getSharedChannel();
        try {
            var stub = ObserverGrpc.newBlockingStub(channel);
            var request = buildRequest(podNamespace, podLabelSelector);

            log.info("Starting Hubble flow observation: namespace={}, selector={}",
                    podNamespace, podLabelSelector);

            var responses = stub.getFlows(request);
            while (responses.hasNext() && !Thread.currentThread().isInterrupted()) {
                var response = responses.next();
                if (!response.hasFlow()) {
                    continue;
                }
                var flow = response.getFlow();

                // Only process L7 flows
                if (flow.getType() != FlowType.L7) {
                    continue;
                }
                var l7 = flow.getL7();
                if (!l7.hasDns()) {
                    continue;
                }
                var dns = l7.getDns();
                var queryName = dns.getQuery();

                if (!domainFilter.isExternalDomain(queryName)) {
                    continue;
                }

                // Strip trailing dot from FQDN (Hubble emits "auth.docker.io." — we store "auth.docker.io")
                String domain = queryName.endsWith(".") ? queryName.substring(0, queryName.length() - 1) : queryName;

                CiliumVerdict verdict = mapVerdict(flow.getVerdict());
                if (verdict == null) {
                    continue; // ignore ERROR, AUDIT, etc.
                }

                long observedAt = flow.hasTime()
                        ? flow.getTime().getSeconds() * 1000L + flow.getTime().getNanos() / 1_000_000L
                        : System.currentTimeMillis();
                onEntry.accept(new DomainEntry(domain, verdict, observedAt));
            }
        } catch (Exception e) {
            if (Thread.currentThread().isInterrupted()) {
                log.warn("Hubble flow observation interrupted: namespace={}, selector={}",
                        podNamespace, podLabelSelector);
            } else {
                log.warn("Hubble flow observation ended unexpectedly: namespace={}, selector={}: {}",
                        podNamespace, podLabelSelector, e.getMessage());
            }
        }
        // Channel lifecycle is managed by HubbleRelayGrpcChannelFactory — do not shut it down here.
        // When this observation's thread is interrupted (future.cancel(true)), gRPC detects the
        // interrupt on the next hasNext() call and throws StatusRuntimeException(CANCELLED),
        // ending only this stream; the shared channel remains open for other observations.
    }

    private GetFlowsRequest buildRequest(String podNamespace, String podLabelSelector) {
        var filter = FlowFilter.newBuilder()
                .addSourceNamespace(podNamespace)
                .addSourceLabel(podLabelSelector)
                .addType(FlowType.L7)
                .build();
        return GetFlowsRequest.newBuilder()
                .setFollow(true)
                .addWhitelist(filter)
                .build();
    }

    /**
     * Maps a Hubble {@link Verdict} to a {@link CiliumVerdict}, returning {@code null} for
     * verdicts that should not be recorded (ERROR, AUDIT, REDIRECTED, etc.).
     */
    private CiliumVerdict mapVerdict(Verdict verdict) {
        return switch (verdict) {
            case FORWARDED -> CiliumVerdict.ALLOWED;
            case DROPPED -> CiliumVerdict.BLOCKED;
            default -> null;
        };
    }
}
