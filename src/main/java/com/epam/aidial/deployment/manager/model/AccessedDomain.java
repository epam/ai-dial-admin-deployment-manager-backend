package com.epam.aidial.deployment.manager.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * One distinct domain accessed during an image build and its Cilium verdict.
 * Outcome is ALLOWED if any access to this domain was allowed by Cilium, otherwise BLOCKED.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AccessedDomain {

    private String domain;
    private AccessVerdict verdict;
}
