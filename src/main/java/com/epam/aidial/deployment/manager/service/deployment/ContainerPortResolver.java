package com.epam.aidial.deployment.manager.service.deployment;

import com.epam.aidial.deployment.manager.configuration.logging.LogExecution;
import org.springframework.stereotype.Component;

import java.util.function.Supplier;

@Component
@LogExecution
public class ContainerPortResolver {

    public int resolveContainerPort(Supplier<Integer> portSupplier, int defaultPort) {
        var port = portSupplier.get();
        if (port != null) {
            return port;
        }
        return defaultPort;
    }
}
