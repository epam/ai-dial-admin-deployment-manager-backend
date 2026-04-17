package com.epam.aidial.deployment.manager.web.dto.page;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SortDto {
    private String column;
    private SortDirectionDto direction;
}
