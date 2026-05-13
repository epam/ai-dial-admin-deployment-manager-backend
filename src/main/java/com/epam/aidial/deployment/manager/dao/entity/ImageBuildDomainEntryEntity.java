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

import java.util.UUID;

@Entity
@Table(name = "image_build_domain_entries")
@Getter
@Setter
@NoArgsConstructor
// Do NOT use @Data — it generates equals/hashCode over all fields, causing JPA collection membership
// bugs when entities are added to Sets before being persisted. @EqualsAndHashCode(of = "id") ensures
// PK-based identity, matching JPA's own notion of entity equality.
@EqualsAndHashCode(of = "id")
@ToString
public class ImageBuildDomainEntryEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private UUID imageDefinitionId;

    private String domain;

    @Enumerated(EnumType.STRING)
    private CiliumVerdict verdict;

    private long observedAt;
}
