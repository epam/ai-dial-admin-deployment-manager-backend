package com.epam.aidial.deployment.manager.model;

public record DomainEntry(String domain, CiliumVerdict verdict, long observedAt) {
}
