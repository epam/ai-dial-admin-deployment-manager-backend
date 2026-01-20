package com.epam.aidial.deployment.manager.mapper;

import com.epam.aidial.deployment.manager.model.EventInfo;
import com.epam.aidial.deployment.manager.model.EventType;
import com.epam.aidial.deployment.manager.model.ObjectKind;
import io.fabric8.kubernetes.api.model.Event;
import io.fabric8.kubernetes.api.model.EventBuilder;
import io.fabric8.kubernetes.api.model.EventSourceBuilder;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.ObjectReference;
import io.fabric8.kubernetes.api.model.ObjectReferenceBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class EventInfoMapperTest {

    private static final String DEPLOYMENT_ID = UUID.randomUUID().toString();
    private static final EventType EVENT_TYPE = EventType.NORMAL;
    private static final String EVENT_REASON = "Created";
    private static final String EVENT_MESSAGE = "Pod created successfully";
    private static final String EVENT_UID = UUID.randomUUID().toString();
    private static final ObjectKind INVOLVED_OBJECT_KIND = ObjectKind.POD;
    private static final String INVOLVED_OBJECT_NAME = "test-pod";
    private static final String INVOLVED_OBJECT_NAMESPACE = "default";
    private static final String SOURCE_COMPONENT = "kubelet";
    private static final String SOURCE_HOST = "node-1";
    private static final String CREATION_TIMESTAMP = "2023-01-01T12:00:00Z";
    private static final String DELETION_TIMESTAMP = "2023-01-01T12:30:00Z";

    private EventInfoMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new EventInfoMapper();
    }

    @Test
    void toEventInfo_shouldMapAllFieldsCorrectly() {
        // Given
        Event event = createFullEvent();

        // When
        EventInfo result = mapper.toEventInfo(event, DEPLOYMENT_ID);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(UUID.fromString(EVENT_UID));
        assertThat(result.getDeploymentId()).isEqualTo(DEPLOYMENT_ID);
        assertThat(result.getEventType()).isEqualTo(EVENT_TYPE);
        assertThat(result.getReason()).isEqualTo(EVENT_REASON);
        assertThat(result.getMessage()).isEqualTo(EVENT_MESSAGE);
        assertThat(result.getCount()).isEqualTo(1);
        assertThat(result.getSource()).contains(SOURCE_COMPONENT);
        assertThat(result.getSource()).contains(SOURCE_HOST);
        assertThat(result.getFirstTimestamp()).isEqualTo(Instant.parse(CREATION_TIMESTAMP));
        assertThat(result.getLastTimestamp()).isEqualTo(Instant.parse(DELETION_TIMESTAMP));
        assertThat(result.getInvolvedObjectKind()).isEqualTo(INVOLVED_OBJECT_KIND);
        assertThat(result.getInvolvedObjectName()).isEqualTo(INVOLVED_OBJECT_NAME);
        assertThat(result.getInvolvedObjectNamespace()).isEqualTo(INVOLVED_OBJECT_NAMESPACE);
    }

    @Test
    void toEventInfo_shouldHandleNullEvent() {
        // When
        EventInfo result = mapper.toEventInfo(null, DEPLOYMENT_ID);

        // Then
        assertThat(result).isNull();
    }

    @Test
    void toEventInfo_shouldHandleNullMetadata() {
        // Given
        Event event = new EventBuilder()
                .withType(EVENT_TYPE.name())
                .withReason(EVENT_REASON)
                .withMessage(EVENT_MESSAGE)
                .withInvolvedObject(createInvolvedObject())
                .withCount(1)
                .build();

        // When
        EventInfo result = mapper.toEventInfo(event, DEPLOYMENT_ID);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getId()).isNull();
        assertThat(result.getFirstTimestamp()).isNull();
        assertThat(result.getLastTimestamp()).isNull();
        assertThat(result.getDeploymentId()).isEqualTo(DEPLOYMENT_ID);
        assertThat(result.getEventType()).isEqualTo(EVENT_TYPE);
        assertThat(result.getReason()).isEqualTo(EVENT_REASON);
        assertThat(result.getMessage()).isEqualTo(EVENT_MESSAGE);
        assertThat(result.getCount()).isEqualTo(1);
    }

    @Test
    void toEventInfo_shouldHandleNullInvolvedObject() {
        // Given
        Event event = new EventBuilder()
                .withType(EVENT_TYPE.name())
                .withReason(EVENT_REASON)
                .withMessage(EVENT_MESSAGE)
                .withMetadata(createMetadata())
                .withCount(1)
                .build();

        // When
        EventInfo result = mapper.toEventInfo(event, DEPLOYMENT_ID);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getInvolvedObjectKind()).isNull();
        assertThat(result.getInvolvedObjectName()).isNull();
        assertThat(result.getInvolvedObjectNamespace()).isNull();
        assertThat(result.getDeploymentId()).isEqualTo(DEPLOYMENT_ID);
        assertThat(result.getEventType()).isEqualTo(EVENT_TYPE);
    }

    @Test
    void toEventInfo_shouldHandleNullCount() {
        // Given
        Event event = new EventBuilder()
                .withType(EVENT_TYPE.name())
                .withReason(EVENT_REASON)
                .withMessage(EVENT_MESSAGE)
                .withMetadata(createMetadata())
                .withInvolvedObject(createInvolvedObject())
                .build(); // No count set

        // When
        EventInfo result = mapper.toEventInfo(event, DEPLOYMENT_ID);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getCount()).isZero();
    }

    @Test
    void toEventInfo_shouldHandleInvalidTimestamps() {
        // Given
        ObjectMeta metadata = new ObjectMeta();
        metadata.setUid(EVENT_UID);
        metadata.setCreationTimestamp("invalid-timestamp");
        metadata.setDeletionTimestamp("also-invalid");

        Event event = new EventBuilder()
                .withType(EVENT_TYPE.name())
                .withReason(EVENT_REASON)
                .withMessage(EVENT_MESSAGE)
                .withMetadata(metadata)
                .withInvolvedObject(createInvolvedObject())
                .withCount(1)
                .build();

        // When
        EventInfo result = mapper.toEventInfo(event, DEPLOYMENT_ID);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getFirstTimestamp()).isNull();
        assertThat(result.getLastTimestamp()).isNull();
    }

    @Test
    void toEventInfo_shouldHandleEmptyTimestamps() {
        // Given
        ObjectMeta metadata = new ObjectMeta();
        metadata.setUid(EVENT_UID);
        metadata.setCreationTimestamp("");
        metadata.setDeletionTimestamp(null);

        Event event = new EventBuilder()
                .withType(EVENT_TYPE.name())
                .withReason(EVENT_REASON)
                .withMessage(EVENT_MESSAGE)
                .withMetadata(metadata)
                .withInvolvedObject(createInvolvedObject())
                .withCount(1)
                .build();

        // When
        EventInfo result = mapper.toEventInfo(event, DEPLOYMENT_ID);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getFirstTimestamp()).isNull();
        assertThat(result.getLastTimestamp()).isNull();
    }

    private Event createFullEvent() {
        return new EventBuilder()
                .withType(EVENT_TYPE.name())
                .withReason(EVENT_REASON)
                .withMessage(EVENT_MESSAGE)
                .withMetadata(createMetadata())
                .withInvolvedObject(createInvolvedObject())
                .withCount(1)
                .withSource(new EventSourceBuilder()
                    .withComponent(SOURCE_COMPONENT)
                    .withHost(SOURCE_HOST)
                    .build())
                .build();
    }

    private ObjectMeta createMetadata() {
        ObjectMeta metadata = new ObjectMeta();
        metadata.setUid(EVENT_UID);
        metadata.setCreationTimestamp(CREATION_TIMESTAMP);
        metadata.setDeletionTimestamp(DELETION_TIMESTAMP);
        return metadata;
    }

    private ObjectReference createInvolvedObject() {
        return new ObjectReferenceBuilder()
                .withKind(INVOLVED_OBJECT_KIND.name())
                .withName(INVOLVED_OBJECT_NAME)
                .withNamespace(INVOLVED_OBJECT_NAMESPACE)
                .build();
    }
}
