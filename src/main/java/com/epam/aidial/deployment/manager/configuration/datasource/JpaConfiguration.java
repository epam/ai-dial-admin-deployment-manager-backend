package com.epam.aidial.deployment.manager.configuration.datasource;

import com.epam.aidial.deployment.manager.dao.entity.JpaEntityPackage;
import com.epam.aidial.deployment.manager.dao.jpa.JpaPackage;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@Configuration("mcpJpaConfiguration")
@EnableJpaRepositories(basePackageClasses = {
        JpaPackage.class,
})
@EntityScan(basePackageClasses = {
        JpaEntityPackage.class
})
@EnableJpaAuditing
public class JpaConfiguration {

}
