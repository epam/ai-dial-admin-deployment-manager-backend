package com.epam.aidial.deployment.manager.kubernetes.event;

import com.epam.aidial.deployment.manager.configuration.logging.LogExecution;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@LogExecution
public class EventReaderFactory {

    public WatchEventReader create(UUID id, EventStreamerConfiguration cfg) {
        return new WatchEventReader(id.toString(), cfg);
    }
}