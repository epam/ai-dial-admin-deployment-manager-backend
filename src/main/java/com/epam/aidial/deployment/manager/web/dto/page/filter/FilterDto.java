package com.epam.aidial.deployment.manager.web.dto.page.filter;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class FilterDto {

    private String column;
    private FilterOperatorDto operator;
    private String value;
}
