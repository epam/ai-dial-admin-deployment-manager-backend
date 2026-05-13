package com.epam.aidial.deployment.manager.dao.entity;

import com.epam.aidial.deployment.manager.model.CiliumVerdict;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * Persisted record of a unique external domain accessed by a deployment pod during its current
 * activation. Cleared on each new deploy activation (FR-014).
 * Deduplication is enforced by the UNIQUE constraint on (deployment_id, domain, verdict).
 */
@Entity
@Table(name = "deployment_domain_entries")
@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode(of = "id")
@ToString
public class DeploymentDomainEntryEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String deploymentId;

    private String domain;

    @Enumerated(EnumType.STRING)
    private CiliumVerdict verdict;

    private long observedAt;
}
