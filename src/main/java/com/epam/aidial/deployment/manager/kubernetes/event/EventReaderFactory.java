package com.epam.aidial.deployment.manager.kubernetes.event;

import com.epam.aidial.deployment.manager.configuration.logging.LogExecution;
import org.springframework.stereotype.Component;

@Component
@LogExecution
public class EventReaderFactory {

    public WatchEventReader create(String id, EventStreamerConfiguration cfg) {
        return new WatchEventReader(id, cfg);
    }
}