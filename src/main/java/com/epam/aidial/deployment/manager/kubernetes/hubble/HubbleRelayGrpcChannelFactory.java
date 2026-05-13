package com.epam.aidial.deployment.manager.kubernetes.hubble;

import com.epam.aidial.deployment.manager.configuration.HubbleRelayProperties;
import com.epam.aidial.deployment.manager.configuration.logging.LogExecution;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.LocalPortForward;
import io.grpc.CallOptions;
import io.grpc.ClientCall;
import io.grpc.ConnectivityState;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.MethodDescriptor;
import io.grpc.netty.shaded.io.grpc.netty.GrpcSslContexts;
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.File;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * Provides a shared gRPC {@link ManagedChannel} to Hubble Relay via a Kubernetes API port-forward.
 *
 * <p><b>Shared-channel model</b>: A single {@link ManagedChannel} (backed by one
 * {@link LocalPortForward} WebSocket tunnel) is reused across all concurrent observations.
 * N parallel builds open N gRPC <em>streams</em> over the shared HTTP/2 connection — not N
 * separate port-forwards. Channel health is checked on each {@link #getSharedChannel()} call;
 * a {@code TRANSIENT_FAILURE} or {@code SHUTDOWN} state triggers recreation of the port-forward
 * and channel. Reconnection timing is governed by the {@code observeWithRetry} loop in
 * {@code HubbleDomainFlowService}. The shared channel is closed at application shutdown via
 * {@link #close()}.
 *
 * <p><b>Direct-connect upgrade path</b>: {@code properties.getHost()} holds the Hubble Relay
 * service hostname for the future NodePort/LoadBalancer path, where the channel would target
 * {@code host:port} directly instead of {@code localhost:localPort}.
 *
 * <p>In port-forward mode (default), TLS is not applied on the localhost channel; transport
 * security is provided by the K8s API TLS + RBAC layer. Set {@code HUBBLE_RELAY_TLS_ENABLED=true}
 * only when upgrading to direct NodePort/LB connectivity.
 */
@Slf4j
@Component
@LogExecution
@RequiredArgsConstructor
public class HubbleRelayGrpcChannelFactory {

    private final HubbleRelayProperties properties;

    // The single KubernetesClient bean (named "deployKubeClient" in production).
    // Injected by type — there is exactly one KubernetesClient bean in both production and test contexts.
    private final KubernetesClient kubeClient;

    // Shared channel — one port-forward for all concurrent observations.
    private volatile ManagedChannel sharedChannel;

    /**
     * Returns the shared gRPC channel, creating it on first call or recreating it if the current
     * channel is no longer usable (port-forward died after a Hubble Relay pod restart).
     *
     * @return the shared {@link ManagedChannel}; callers must NOT shut it down
     * @throws IllegalStateException if no matching Hubble Relay pod is found
     */
    public synchronized ManagedChannel getSharedChannel() {
        if (sharedChannel != null && isChannelUsable(sharedChannel)) {
            return sharedChannel;
        }
        if (sharedChannel != null) {
            sharedChannel.shutdownNow(); // closes old LocalPortForward via PortForwardChannel
        }
        sharedChannel = openNewChannel();
        return sharedChannel;
    }

    /**
     * Closes the shared channel and its underlying port-forward at application shutdown.
     */
    @PreDestroy
    public void close() {
        synchronized (this) {
            if (sharedChannel != null) {
                sharedChannel.shutdownNow();
                sharedChannel = null;
            }
        }
    }

    private boolean isChannelUsable(ManagedChannel channel) {
        if (channel.isShutdown()) {
            return false;
        }
        ConnectivityState state = channel.getState(false);
        return state != ConnectivityState.TRANSIENT_FAILURE && state != ConnectivityState.SHUTDOWN;
    }

    private ManagedChannel openNewChannel() {
        var namespace = properties.getNamespace();
        var labelSelector = properties.getPodLabelSelector();
        var port = properties.getPort();

        var labels = parseLabelSelector(labelSelector);
        var pods = kubeClient.pods().inNamespace(namespace).withLabels(labels).list().getItems();
        if (pods.isEmpty()) {
            throw new IllegalStateException(
                    "No Hubble Relay pod found in namespace '%s' matching selector '%s'"
                            .formatted(namespace, labelSelector));
        }
        var podName = pods.get(0).getMetadata().getName();
        log.info("Opening port-forward to Hubble Relay pod {}/{} port {}", namespace, podName, port);

        var portForward = kubeClient.pods().inNamespace(namespace).withName(podName).portForward(port);
        var localPort = portForward.getLocalPort();
        log.info("Port-forward established: localhost:{} -> {}/{}", localPort, namespace, podName);

        ManagedChannel channel = buildChannel(localPort);
        return new PortForwardChannel(channel, portForward);
    }

    private ManagedChannel buildChannel(int localPort) {
        if (properties.isTlsEnabled()) {
            try {
                var sslContext = GrpcSslContexts.forClient()
                        .trustManager(new File(properties.getCaCertPath()))
                        .build();
                return NettyChannelBuilder.forAddress("localhost", localPort)
                        .sslContext(sslContext)
                        .build();
            } catch (Exception e) {
                throw new IllegalStateException("Failed to build TLS gRPC channel to Hubble Relay", e);
            }
        }
        return ManagedChannelBuilder.forAddress("localhost", localPort)
                .usePlaintext()
                .build();
    }

    /**
     * Parses a Kubernetes label selector string (e.g. {@code "k8s-app=hubble-relay,tier=control"})
     * into a {@code Map<String, String>} for use with {@code withLabels()}.
     * Supports equality-based requirements ({@code key=value}) only — set-based operators
     * ({@code in}, {@code notin}, {@code !=}) are not handled and will be silently ignored.
     */
    static Map<String, String> parseLabelSelector(String labelSelector) {
        var labels = new HashMap<String, String>();
        Arrays.stream(labelSelector.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .forEach(pair -> {
                    int eq = pair.indexOf('=');
                    if (eq > 0) {
                        labels.put(pair.substring(0, eq).trim(), pair.substring(eq + 1).trim());
                    }
                });
        return labels;
    }

    /**
     * A {@link ManagedChannel} that also closes its underlying {@link LocalPortForward} on
     * {@code shutdown()} / {@code shutdownNow()}, ensuring the K8s API WebSocket tunnel is
     * released when the observation finishes.
     */
    private static final class PortForwardChannel extends ManagedChannel {

        private final ManagedChannel delegate;
        private final LocalPortForward portForward;

        private PortForwardChannel(ManagedChannel delegate, LocalPortForward portForward) {
            this.delegate = delegate;
            this.portForward = portForward;
        }

        @Override
        public ManagedChannel shutdown() {
            delegate.shutdown();
            closePortForward();
            return this;
        }

        @Override
        public boolean isShutdown() {
            return delegate.isShutdown();
        }

        @Override
        public ConnectivityState getState(boolean requestConnection) {
            return delegate.getState(requestConnection);
        }

        @Override
        public boolean isTerminated() {
            return delegate.isTerminated();
        }

        @Override
        public ManagedChannel shutdownNow() {
            delegate.shutdownNow();
            closePortForward();
            return this;
        }

        @Override
        public boolean awaitTermination(long timeout, java.util.concurrent.TimeUnit unit) throws InterruptedException {
            return delegate.awaitTermination(timeout, unit);
        }

        @Override
        public <RequestT, ResponseT> ClientCall<RequestT, ResponseT> newCall(
                MethodDescriptor<RequestT, ResponseT> methodDescriptor, CallOptions callOptions) {
            return delegate.newCall(methodDescriptor, callOptions);
        }

        @Override
        public String authority() {
            return delegate.authority();
        }

        private void closePortForward() {
            try {
                portForward.close();
            } catch (Exception e) {
                // port-forward close errors are non-fatal; observation has already finished
            }
        }
    }
}
