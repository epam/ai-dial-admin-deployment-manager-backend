package com.epam.aidial.deployment.manager.dao.entity;

import com.epam.aidial.deployment.manager.model.ComponentType;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Embeddable
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ComponentId {
    private String id;
    @Enumerated(value = EnumType.STRING)
    private ComponentType type;
}
