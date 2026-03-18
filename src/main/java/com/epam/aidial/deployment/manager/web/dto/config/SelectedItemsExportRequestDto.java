package com.epam.aidial.deployment.manager.web.dto.config;

import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class SelectedItemsExportRequestDto extends ExportRequestDto {

    @Valid
    private List<ExportConfigComponentDto> components;
}
