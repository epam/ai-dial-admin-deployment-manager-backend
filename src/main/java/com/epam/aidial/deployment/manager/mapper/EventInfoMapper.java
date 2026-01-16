package com.epam.aidial.deployment.manager.mapper;

import com.epam.aidial.deployment.manager.configuration.logging.LogExecution;
import com.epam.aidial.deployment.manager.model.EventInfo;
import com.epam.aidial.deployment.manager.model.EventType;
import com.epam.aidial.deployment.manager.model.ObjectKind;
import io.fabric8.kubernetes.api.model.Event;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Slf4j
@Service
@LogExecution
public class EventInfoMapper {

    public EventInfo toEventInfo(Event event, String deploymentId) {
        if (event == null) {
            return null;
        }

        var eventInfoBuilder = EventInfo.builder()
                .deploymentId(deploymentId)
                .eventType(EventType.fromString(event.getType()))
                .reason(event.getReason())
                .message(event.getMessage())
                .count(event.getCount() != null ? event.getCount() : 0)
                .source(event.getSource() != null ? event.getSource().toString() : null);

        var metadata = event.getMetadata();
        if (metadata != null) {
            eventInfoBuilder
                    .id(UUID.fromString(metadata.getUid()))
                    .firstTimestamp(parseInstant(metadata.getCreationTimestamp()))
                    .lastTimestamp(parseInstant(metadata.getDeletionTimestamp()));
        }

        var involvedObject = event.getInvolvedObject();
        if (involvedObject != null) {
            eventInfoBuilder
                    .involvedObjectNamespace(involvedObject.getNamespace())
                    .involvedObjectName(involvedObject.getName())
                    .involvedObjectKind(ObjectKind.fromString(involvedObject.getKind()));
        }

        var eventInfo = eventInfoBuilder.build();
        log.debug("Successfully mapped Kubernetes event to EventInfo. Deployment: {}. Event: {}. EventInfo: {}",
                deploymentId, event, eventInfo);

        return eventInfo;
    }

    private static Instant parseInstant(String timestamp) {
        if (StringUtils.isEmpty(timestamp)) {
            return null;
        }
        try {
            return Instant.parse(timestamp);
        } catch (Exception e) {
            log.debug("Failed to parse timestamp '{}' to Instant", timestamp, e);
            return null;
        }
    }
}