package com.epam.aidial.deployment.manager.model.config;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class SelectedItemsExportRequest extends ExportRequest {
    private List<ExportConfigComponent> components;
}
