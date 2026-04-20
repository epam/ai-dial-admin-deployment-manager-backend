package com.epam.aidial.deployment.manager.web.dto;

import lombok.Data;
import lombok.experimental.SuperBuilder;

import java.util.Collection;

@Data
@SuperBuilder
public class PageDto<T> {

    private long total;
    private int totalPages;
    private Collection<T> data;
}
