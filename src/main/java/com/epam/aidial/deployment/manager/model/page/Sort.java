package com.epam.aidial.deployment.manager.model.page;

import lombok.Data;

@Data
public class Sort {
    private String column;
    private SortDirection direction;
}
