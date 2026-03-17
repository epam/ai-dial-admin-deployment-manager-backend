package com.epam.aidial.deployment.manager.web.dto.config;

public record ImportComponentDto<T>(ImportActionDto importAction, T prev, T next) {}
