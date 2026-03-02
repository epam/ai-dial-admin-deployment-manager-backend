package com.epam.aidial.deployment.manager.dao.entity.deployment;

import com.epam.aidial.deployment.manager.dao.entity.PersistenceMcpTransport;
import jakarta.persistence.Column;
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
@Table(name = "mcp_deployment")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class McpDeploymentEntity extends DeploymentEntity {

    @Enumerated(value = EnumType.STRING)
    private PersistenceMcpTransport transport;

    @Column(name = "image_reference")
    private String imageReference;

    @Column(name = "mcp_endpoint_path")
    private String mcpEndpointPath;
}
