package com.epam.aidial.deployment.manager.model;

/**
 * An observed external domain access captured from Hubble Relay DNS proxy flows.
 * Placed in the {@code model} package so it is accessible from both the
 * {@code kubernetes/hubble/} layer (which produces entries) and the {@code service/} layer
 * (which consumes them) without cross-layer imports.
 *
 * @param domain     bare external FQDN (e.g. {@code "auth.docker.io"}) — trailing dot stripped
 * @param verdict    {@link CiliumVerdict#ALLOWED} (FORWARDED) or {@link CiliumVerdict#BLOCKED} (DROPPED)
 * @param observedAt epoch milliseconds when the flow was observed
 */
public record DomainEntry(String domain, CiliumVerdict verdict, long observedAt) {
}
