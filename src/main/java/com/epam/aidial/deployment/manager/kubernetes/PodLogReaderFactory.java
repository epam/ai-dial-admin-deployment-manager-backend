package com.epam.aidial.deployment.manager.kubernetes;

import com.epam.aidial.deployment.manager.configuration.logging.LogExecution;
import org.springframework.stereotype.Component;

@Component
@LogExecution
public class PodLogReaderFactory {

    public PodLogReader create(PodLogReaderConfiguration configuration) {
        return new PodLogReader(configuration);
    }

}
