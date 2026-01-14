package com.epam.aidial.deployment.manager.dao.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Entity
@Table(name = "mcp_image_definition")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class McpImageDefinitionEntity extends ImageDefinitionEntity {

    @Enumerated(value = EnumType.STRING)
    private PersistenceMcpTransportType transportType;
}
