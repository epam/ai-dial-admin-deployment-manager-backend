package com.epam.aidial.deployment.manager.model.config;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ExportConfigComponent {
    private String name;
    private ExportConfigComponentType type;
}