package com.epam.aidial.deployment.manager.dao.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Table;
import lombok.NoArgsConstructor;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Entity
@Table(name = "application_image_definition")
@EntityListeners(AuditingEntityListener.class)
@NoArgsConstructor
public class ApplicationImageDefinitionEntity extends ImageDefinitionEntity {

}
