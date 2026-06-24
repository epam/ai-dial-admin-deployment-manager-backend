package com.epam.aidial.deployment.manager.configuration;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.log4j.appender.v2_17.OpenTelemetryAppender;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Configuration;

/**
 * Routes log4j2 events captured by the {@code <OpenTelemetry>} appender (log4j2.xml) into the
 * OpenTelemetry SDK configured by spring-boot-starter-opentelemetry. Unlike the former community
 * starter, the official starter does not install the appender automatically.
 */
@Configuration
public class OpenTelemetryLogAppenderConfiguration {

    public OpenTelemetryLogAppenderConfiguration(ObjectProvider<OpenTelemetry> openTelemetry) {
        openTelemetry.ifAvailable(OpenTelemetryAppender::install);
    }
}
