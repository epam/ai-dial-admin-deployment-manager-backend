package com.epam.aidial.deployment.manager.model;

import lombok.Data;
import lombok.experimental.SuperBuilder;

import java.util.Collection;

@Data
@SuperBuilder
public class Page<T> {

    private long total;
    private int totalPages;
    private Collection<T> data;
}
