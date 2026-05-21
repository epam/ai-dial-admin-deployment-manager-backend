package com.epam.aidial.deployment.manager.model;

public enum CiliumVerdict {
    ALLOWED,   // maps from Hubble flow verdict FORWARDED
    BLOCKED    // maps from Hubble flow verdict DROPPED
}
