package com.epam.aidial.deployment.manager.dao.audit.entity;

import com.epam.aidial.deployment.manager.dao.audit.listener.AuditRevisionListener;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.ToString;
import org.hibernate.envers.RevisionEntity;
import org.hibernate.envers.RevisionNumber;
import org.hibernate.envers.RevisionTimestamp;

import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "revinfo")
@RevisionEntity(AuditRevisionListener.class)
@Data
public class AuditRevisionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @RevisionNumber
    private Integer id;

    @RevisionTimestamp
    private Long timestamp;

    private String author;

    private String email;

    @ToString.Exclude
    @OneToMany(cascade = CascadeType.ALL, mappedBy = "revision", orphanRemoval = true)
    private Set<AuditActivityEntity> activities = new HashSet<>();
}
