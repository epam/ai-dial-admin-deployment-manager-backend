package com.epam.aidial.deployment.manager.dao.repository;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@ComponentScan(basePackages = {"com.epam.aidial.deployment.manager.dao.mapper"})
public class MappersConfig {
}
