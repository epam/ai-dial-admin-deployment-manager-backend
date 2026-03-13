package com.epam.aidial.deployment.manager.dao.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Persistence shape for one accessed domain (JSON column).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PersistenceAccessedDomain {

    private String domain;
    private String verdict;
}
