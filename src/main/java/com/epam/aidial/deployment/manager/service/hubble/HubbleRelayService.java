package com.epam.aidial.deployment.manager.service.hubble;

import com.epam.aidial.deployment.manager.configuration.logging.LogExecution;
import com.epam.aidial.deployment.manager.model.AccessVerdict;
import com.epam.aidial.deployment.manager.service.ImageDefinitionService;
import com.epam.aidial.deployment.manager.service.SafeAutoCloseable;
import flow.FlowOuterClass;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import observer.ObserverGrpc;
import observer.ObserverOuterClass;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Integrates with Hubble Relay gRPC API to stream network flows for a build pod
 * and persist accessed domains (DNS queries with Cilium verdict) via ImageDefinitionService.
 */
@Slf4j
@Service
@LogExecution
@RequiredArgsConstructor
public class HubbleRelayService {

    private static final int SHUTDOWN_TIMEOUT_SEC = 5;

    private final ImageDefinitionService imageDefinitionService;

    @Value("${app.hubble-relay.host:hubble-relay.cilium.svc.cluster.local}")
    private String hubbleRelayHost;
    @Value("${app.hubble-relay.port:80}")
    private int hubbleRelayPort;

    /**
     * Starts a background stream of Hubble flows for the given pod filter, maps DNS flows
     * to domain + verdict, and appends them via ImageDefinitionService. Returns a handle
     * to cancel the stream and close the channel.
     *
     * @param imageDefinitionId image definition (build) id
     * @param namespace         pod namespace (e.g. from JobSpecification)
     * @param podNamePrefix     pod name prefix (e.g. "dm-base-{imageDefinitionId}" from generateName(jobId))
     * @return closeable to stop the stream and release the channel
     */
    public SafeAutoCloseable streamAndCollectDomains(
            UUID imageDefinitionId,
            String namespace,
            String podNamePrefix
    ) {
        String sourcePodFilter = namespace + "/" + podNamePrefix;
        ManagedChannel channel = ManagedChannelBuilder
                .forAddress(hubbleRelayHost, hubbleRelayPort)
                .usePlaintext()
                .build();

        FlowOuterClass.FlowFilter filter = FlowOuterClass.FlowFilter.newBuilder()
                .addSourcePod(sourcePodFilter)
                .build();

        ObserverOuterClass.GetFlowsRequest request = ObserverOuterClass.GetFlowsRequest.newBuilder()
                .setFollow(true)
                .addWhitelist(filter)
                .build();

        AtomicBoolean closed = new AtomicBoolean(false);

        StreamObserver<ObserverOuterClass.GetFlowsResponse> responseObserver = new StreamObserver<>() {
            @Override
            public void onNext(ObserverOuterClass.GetFlowsResponse response) {
                if (closed.get()) {
                    return;
                }
                if (response.hasFlow()) {
                    FlowOuterClass.Flow flow = response.getFlow();
                    if (flow.hasL7() && flow.getL7().hasDns()) {
                        String query = flow.getL7().getDns().getQuery();
                        if (StringUtils.isNotBlank(query)) {
                            String domain = query.endsWith(".") ? query.substring(0, query.length() - 1) : query;
                            AccessVerdict verdict = flow.getVerdict() == FlowOuterClass.Verdict.FORWARDED
                                    ? AccessVerdict.ALLOWED
                                    : AccessVerdict.BLOCKED;
                            try {
                                imageDefinitionService.addAccessedDomain(imageDefinitionId, domain, verdict);
                            } catch (Exception e) {
                                log.warn("Failed to add accessed domain for image {}: {}", imageDefinitionId, e.getMessage());
                            }
                        }
                    }
                }
            }

            @Override
            public void onError(Throwable t) {
                log.warn("Hubble stream error for image {}: {}", imageDefinitionId, t.getMessage());
            }

            @Override
            public void onCompleted() {
                log.debug("Hubble stream completed for image {}", imageDefinitionId);
            }
        };

        ObserverGrpc.ObserverStub stub = ObserverGrpc.newStub(channel);
        stub.getFlows(request, responseObserver);

        return () -> {
            if (closed.compareAndSet(false, true)) {
                try {
                    channel.shutdown();
                    if (!channel.awaitTermination(SHUTDOWN_TIMEOUT_SEC, TimeUnit.SECONDS)) {
                        channel.shutdownNow();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    channel.shutdownNow();
                }
            }
        };
    }
}
