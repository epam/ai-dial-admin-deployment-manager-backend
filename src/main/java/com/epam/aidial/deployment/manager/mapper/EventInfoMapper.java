package com.epam.aidial.deployment.manager.mapper;

import com.epam.aidial.deployment.manager.configuration.logging.LogExecution;
import com.epam.aidial.deployment.manager.model.EventInfo;
import com.epam.aidial.deployment.manager.model.EventType;
import com.epam.aidial.deployment.manager.model.ObjectKind;
import com.epam.aidial.deployment.manager.utils.K8sParseUtils;
import io.fabric8.kubernetes.api.model.Event;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

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
                    .firstTimestamp(K8sParseUtils.parseInstant(metadata.getCreationTimestamp()))
                    .lastTimestamp(K8sParseUtils.parseInstant(metadata.getDeletionTimestamp()));
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

}