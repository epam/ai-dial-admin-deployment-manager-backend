package com.epam.aidial.deployment.manager.model.config;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ImportComponent<T> {

    private ImportAction action;
    private T prev;
    private T next;
}
