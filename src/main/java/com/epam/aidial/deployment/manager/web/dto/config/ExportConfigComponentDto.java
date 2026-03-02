package com.epam.aidial.deployment.manager.web.dto.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExportConfigComponentDto {

    @NotBlank
    private String name;
    @NotNull
    private ExportConfigComponentTypeDto type;
}
