package com.epam.aidial.deployment.manager.service.deployment;

import com.epam.aidial.deployment.manager.kubernetes.event.EventReaderFactory;
import com.epam.aidial.deployment.manager.kubernetes.event.EventStreamerConfiguration;
import com.epam.aidial.deployment.manager.kubernetes.event.WatchEventReader;
import com.epam.aidial.deployment.manager.mapper.EventInfoMapper;
import com.epam.aidial.deployment.manager.model.EventInfo;
import com.epam.aidial.deployment.manager.model.EventType;
import com.epam.aidial.deployment.manager.model.ObjectKind;
import com.epam.aidial.deployment.manager.service.SafeAutoCloseable;
import com.epam.aidial.deployment.manager.service.SseEmitterFactory;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import io.fabric8.kubernetes.api.model.Event;
import io.fabric8.kubernetes.api.model.EventBuilder;
import io.fabric8.kubernetes.api.model.EventList;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.ObjectReferenceBuilder;
import io.fabric8.kubernetes.client.dsl.NonNamespaceOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SuppressWarnings({"unchecked", "resource"})
@ExtendWith(MockitoExtension.class)
public class EventStreamingServiceTest {

    private static final String DEPLOYMENT_ID = String.valueOf(UUID.randomUUID());
    private static final String EVENT_REASON = "Created";
    private static final String EVENT_MESSAGE = "Pod created successfully";
    private static final String EVENT_UID = UUID.randomUUID().toString();
    private static final ObjectKind INVOLVED_OBJECT_KIND = ObjectKind.POD;
    private static final String INVOLVED_OBJECT_NAME = "test-pod";
    private static final String INVOLVED_OBJECT_NAMESPACE = "default";

    @Mock
    private EventReaderFactory eventReaderFactory;
    @Mock
    private SseEmitterFactory sseEmitterFactory;
    @Mock
    private DeploymentManagerProvider deploymentManagerProvider;
    @Mock
    private DeploymentManager deploymentManager;
    @Mock
    private EventInfoMapper eventInfoMapper;
    @Mock
    private NonNamespaceOperation<Event, EventList, Resource<Event>> eventSource;
    @Mock
    private WatchEventReader watchEventReader;
    @Mock
    private SseEmitter sseEmitter;

    @Spy
    private final ExecutorService executorService = MoreExecutors.newDirectExecutorService();

    @InjectMocks
    private EventStreamingService eventStreamingService;

    @Captor
    private ArgumentCaptor<Function<SseEmitter, SafeAutoCloseable>> emitterConsumerCaptor;

    private EventStreamerConfiguration eventStreamerConfig;
    private EventInfo testEventInfo;
    private Event testEvent;

    @BeforeEach
    void setUp() {
        eventStreamerConfig = EventStreamerConfiguration.builder()
                .sinceTime(Instant.now().minusSeconds(3600))
                .eventType(EventType.NORMAL)
                .involvedObjectKind(INVOLVED_OBJECT_KIND)
                .build();

        var involvedObject = new ObjectReferenceBuilder()
                .withKind(INVOLVED_OBJECT_KIND.name())
                .withName(INVOLVED_OBJECT_NAME)
                .withNamespace(INVOLVED_OBJECT_NAMESPACE)
                .build();

        var metadata = new ObjectMeta();
        metadata.setUid(EVENT_UID);
        metadata.setCreationTimestamp(Instant.now().toString());

        testEvent = new EventBuilder()
                .withType(EventType.NORMAL.name())
                .withReason(EVENT_REASON)
                .withMessage(EVENT_MESSAGE)
                .withInvolvedObject(involvedObject)
                .withMetadata(metadata)
                .withCount(1)
                .build();

        testEventInfo = EventInfo.builder()
                .id(UUID.fromString(EVENT_UID))
                .deploymentId(DEPLOYMENT_ID)
                .eventType(EventType.NORMAL)
                .reason(EVENT_REASON)
                .message(EVENT_MESSAGE)
                .involvedObjectKind(INVOLVED_OBJECT_KIND)
                .involvedObjectName(INVOLVED_OBJECT_NAME)
                .involvedObjectNamespace(INVOLVED_OBJECT_NAMESPACE)
                .count(1)
                .build();

        when(deploymentManagerProvider.provide(DEPLOYMENT_ID)).thenReturn(deploymentManager);
        when(deploymentManager.getAllEventsBase()).thenReturn(eventSource);
    }

    @Test
    void streamEvents_shouldCreateEmitterAndStartEventStreaming() {
        // Given
        when(sseEmitterFactory.createEmitter(
            eq(DEPLOYMENT_ID),
            anyString(),
            any(Function.class)
        )).thenReturn(sseEmitter);

        when(eventReaderFactory.create(eq(DEPLOYMENT_ID), any(EventStreamerConfiguration.class)))
                .thenReturn(watchEventReader);

        // When
        SseEmitter result = eventStreamingService.streamEvents(DEPLOYMENT_ID, eventStreamerConfig);

        // Then
        assertThat(result).isEqualTo(sseEmitter);
        verify(sseEmitterFactory).createEmitter(
                eq(DEPLOYMENT_ID),
                eq("Deployment-" + DEPLOYMENT_ID),
                emitterConsumerCaptor.capture()
        );

        // Simulate the emitter initialization
        emitterConsumerCaptor.getValue().apply(sseEmitter);

        // Verify event source and reader were created and used correctly
        verify(deploymentManagerProvider).provide(DEPLOYMENT_ID);
        verify(deploymentManager).getAllEventsBase();
        verify(eventReaderFactory).create(eq(DEPLOYMENT_ID), eq(eventStreamerConfig));
        verify(watchEventReader).readEvents(eq(eventSource), any(Consumer.class));

        // Verify executor service was used
        verify(executorService).submit(any(Runnable.class));
    }

    @Test
    void streamEvents_shouldSendEventsToEmitterAndSaveToRepository() throws IOException {
        // Given
        when(eventInfoMapper.toEventInfo(testEvent, DEPLOYMENT_ID)).thenReturn(testEventInfo);
        when(eventReaderFactory.create(eq(DEPLOYMENT_ID), any(EventStreamerConfiguration.class)))
                .thenReturn(watchEventReader);

        when(sseEmitterFactory.createEmitter(
            eq(DEPLOYMENT_ID),
            anyString(),
            any(Function.class)
        )).thenReturn(sseEmitter);

        // Simulate event reading
        doAnswer(invocation -> {
            Consumer<Event> eventConsumer = invocation.getArgument(1);
            eventConsumer.accept(testEvent);
            return null;
        }).when(watchEventReader).readEvents(eq(eventSource), any(Consumer.class));

        // When
        eventStreamingService.streamEvents(DEPLOYMENT_ID, eventStreamerConfig);
        verify(sseEmitterFactory).createEmitter(
                eq(DEPLOYMENT_ID),
                eq("Deployment-" + DEPLOYMENT_ID),
                emitterConsumerCaptor.capture()
        );
        emitterConsumerCaptor.getValue().apply(sseEmitter);

        // Then
        verify(eventInfoMapper).toEventInfo(testEvent, DEPLOYMENT_ID);
        verify(sseEmitter).send(any(SseEmitter.SseEventBuilder.class));
    }

    @Test
    void streamEvents_shouldHandleMultipleEvents() throws IOException {
        // Given
        var secondEvent = createTestEvent();
        var secondEventInfo = EventInfo.builder()
                .id(UUID.fromString(EVENT_UID))
                .deploymentId(DEPLOYMENT_ID)
                .eventType(EventType.WARNING)
                .reason("Unhealthy")
                .message("Pod is unhealthy")
                .build();

        when(eventInfoMapper.toEventInfo(testEvent, DEPLOYMENT_ID)).thenReturn(testEventInfo);
        when(eventReaderFactory.create(eq(DEPLOYMENT_ID), any(EventStreamerConfiguration.class)))
                .thenReturn(watchEventReader);

        when(sseEmitterFactory.createEmitter(
            eq(DEPLOYMENT_ID),
            anyString(),
            any(Function.class)
        )).thenReturn(sseEmitter);

        when(eventInfoMapper.toEventInfo(secondEvent, DEPLOYMENT_ID)).thenReturn(secondEventInfo);

        doAnswer(invocation -> {
            Consumer<Event> eventConsumer = invocation.getArgument(1);
            eventConsumer.accept(testEvent);
            eventConsumer.accept(secondEvent);
            return null;
        }).when(watchEventReader).readEvents(eq(eventSource), any(Consumer.class));

        // When
        eventStreamingService.streamEvents(DEPLOYMENT_ID, eventStreamerConfig);
        verify(sseEmitterFactory).createEmitter(
                eq(DEPLOYMENT_ID),
                eq("Deployment-" + DEPLOYMENT_ID),
                emitterConsumerCaptor.capture()
        );
        emitterConsumerCaptor.getValue().apply(sseEmitter);

        // Then
        verify(sseEmitter, times(2)).send(any(SseEmitter.SseEventBuilder.class));
    }

    @Test
    void streamEvents_shouldHandleExceptionDuringEventReading() {
        // Given
        when(sseEmitterFactory.createEmitter(
                eq(DEPLOYMENT_ID),
                anyString(),
                any(Function.class)
        )).thenReturn(sseEmitter);

        when(eventReaderFactory.create(eq(DEPLOYMENT_ID), any(EventStreamerConfiguration.class)))
                .thenReturn(watchEventReader);

        // Simulate exception during event reading
        doThrow(new RuntimeException("Test exception")).when(watchEventReader)
                .readEvents(eq(eventSource), any(Consumer.class));

        // When
        eventStreamingService.streamEvents(DEPLOYMENT_ID, eventStreamerConfig);
        verify(sseEmitterFactory).createEmitter(
                eq(DEPLOYMENT_ID),
                eq("Deployment-" + DEPLOYMENT_ID),
                emitterConsumerCaptor.capture()
        );
        emitterConsumerCaptor.getValue().apply(sseEmitter);

        // Then
        verify(sseEmitter).completeWithError(any(RuntimeException.class));
        verify(watchEventReader).close();
    }

    @Test
    void streamEvents_shouldHandleDataIntegrityViolationException() throws IOException {
        // Given
        when(sseEmitterFactory.createEmitter(
                eq(DEPLOYMENT_ID),
                anyString(),
                any(Function.class)
        )).thenReturn(sseEmitter);

        when(eventInfoMapper.toEventInfo(testEvent, DEPLOYMENT_ID)).thenReturn(testEventInfo);
        when(eventReaderFactory.create(eq(DEPLOYMENT_ID), any(EventStreamerConfiguration.class)))
                .thenReturn(watchEventReader);

        // Simulate event reading with DataIntegrityViolationException when sending
        doAnswer(invocation -> {
            Consumer<Event> eventConsumer = invocation.getArgument(1);
            eventConsumer.accept(testEvent);
            return null;
        }).when(watchEventReader).readEvents(eq(eventSource), any(Consumer.class));

        // Simulate DataIntegrityViolationException when sending the event
        doThrow(new DataIntegrityViolationException("Deployment was deleted"))
                .when(sseEmitter).send(any(SseEmitter.SseEventBuilder.class));

        // When
        eventStreamingService.streamEvents(DEPLOYMENT_ID, eventStreamerConfig);
        verify(sseEmitterFactory).createEmitter(
                eq(DEPLOYMENT_ID),
                eq("Deployment-" + DEPLOYMENT_ID),
                emitterConsumerCaptor.capture()
        );
        emitterConsumerCaptor.getValue().apply(sseEmitter);

        // Then
        verify(sseEmitter).completeWithError(any(DataIntegrityViolationException.class));
        verify(watchEventReader).close();
    }

    @Test
    void streamEvents_shouldHandleIoExceptionDuringSendingEvents() throws IOException {
        // Given
        when(sseEmitterFactory.createEmitter(
                eq(DEPLOYMENT_ID),
                anyString(),
                any(Function.class)
        )).thenReturn(sseEmitter);

        when(eventInfoMapper.toEventInfo(testEvent, DEPLOYMENT_ID)).thenReturn(testEventInfo);
        when(eventReaderFactory.create(eq(DEPLOYMENT_ID), any(EventStreamerConfiguration.class)))
                .thenReturn(watchEventReader);

        // Simulate event reading
        doAnswer(invocation -> {
            Consumer<Event> eventConsumer = invocation.getArgument(1);
            eventConsumer.accept(testEvent);
            return null;
        }).when(watchEventReader).readEvents(eq(eventSource), any(Consumer.class));

        // Simulate IOException when sending the event
        doThrow(new IOException("Connection closed"))
                .when(sseEmitter).send(any(SseEmitter.SseEventBuilder.class));

        // When
        eventStreamingService.streamEvents(DEPLOYMENT_ID, eventStreamerConfig);
        verify(sseEmitterFactory).createEmitter(
                eq(DEPLOYMENT_ID),
                eq("Deployment-" + DEPLOYMENT_ID),
                emitterConsumerCaptor.capture()
        );
        emitterConsumerCaptor.getValue().apply(sseEmitter);

        // Then
        verify(sseEmitter).completeWithError(any(IOException.class));
        verify(watchEventReader).close();
    }

    @Test
    void streamEvents_shouldCompleteEmitterWhenEventReadingFinishes() {
        // Given
        when(sseEmitterFactory.createEmitter(
                eq(DEPLOYMENT_ID),
                anyString(),
                any(Function.class)
        )).thenReturn(sseEmitter);

        when(eventReaderFactory.create(eq(DEPLOYMENT_ID), any(EventStreamerConfiguration.class)))
                .thenReturn(watchEventReader);

        // Simulate event reading that completes normally
        doAnswer(invocation -> {
            Consumer<Event> eventConsumer = invocation.getArgument(1);
            eventConsumer.accept(testEvent);
            return null;
        }).when(watchEventReader).readEvents(eq(eventSource), any(Consumer.class));

        // When
        eventStreamingService.streamEvents(DEPLOYMENT_ID, eventStreamerConfig);
        verify(sseEmitterFactory).createEmitter(
                eq(DEPLOYMENT_ID),
                eq("Deployment-" + DEPLOYMENT_ID),
                emitterConsumerCaptor.capture()
        );
        emitterConsumerCaptor.getValue().apply(sseEmitter);

        // Then
        verify(sseEmitter).complete();
        verify(watchEventReader).close();
    }

    @Test
    void streamEvents_shouldCancelFutureWhenClosed() {
        // Given
        when(sseEmitterFactory.createEmitter(
                eq(DEPLOYMENT_ID),
                anyString(),
                any(Function.class)
        )).thenReturn(sseEmitter);

        when(eventReaderFactory.create(eq(DEPLOYMENT_ID), any(EventStreamerConfiguration.class)))
                .thenReturn(watchEventReader);

        var mockFuture = mock(ListenableFuture.class);
        doReturn(mockFuture).when(executorService).submit(any(Runnable.class));

        // When
        eventStreamingService.streamEvents(DEPLOYMENT_ID, eventStreamerConfig);
        verify(sseEmitterFactory).createEmitter(
                eq(DEPLOYMENT_ID),
                eq("Deployment-" + DEPLOYMENT_ID),
                emitterConsumerCaptor.capture()
        );

        SafeAutoCloseable closeable = emitterConsumerCaptor.getValue().apply(sseEmitter);
        closeable.close();

        // Then
        verify(mockFuture).cancel(true);
    }

    @Test
    void streamEvents_withDifferentConfigurations() {
        // Given
        var sinceTimeConfig = EventStreamerConfiguration.builder()
                .sinceTime(Instant.now().minusSeconds(3600))
                .build();
        var eventTypeConfig = EventStreamerConfiguration.builder()
                .eventType(EventType.WARNING)
                .build();

        var sinceTimeReader = mock(WatchEventReader.class);
        var eventTypeReader = mock(WatchEventReader.class);

        when(eventReaderFactory.create(eq(DEPLOYMENT_ID), eq(sinceTimeConfig))).thenReturn(sinceTimeReader);
        when(eventReaderFactory.create(eq(DEPLOYMENT_ID), eq(eventTypeConfig))).thenReturn(eventTypeReader);

        when(sseEmitterFactory.createEmitter(
                eq(DEPLOYMENT_ID),
                anyString(),
                any(Function.class)
        )).thenReturn(sseEmitter);

        // When
        eventStreamingService.streamEvents(DEPLOYMENT_ID, sinceTimeConfig);
        verify(sseEmitterFactory).createEmitter(
                eq(DEPLOYMENT_ID),
                eq("Deployment-" + DEPLOYMENT_ID),
                emitterConsumerCaptor.capture()
        );
        emitterConsumerCaptor.getValue().apply(sseEmitter);

        // Then
        verify(eventReaderFactory).create(DEPLOYMENT_ID, sinceTimeConfig);
        verify(sinceTimeReader).readEvents(eq(eventSource), any(Consumer.class));

        // When
        eventStreamingService.streamEvents(DEPLOYMENT_ID, eventTypeConfig);
        verify(sseEmitterFactory, times(2)).createEmitter(
                eq(DEPLOYMENT_ID),
                eq("Deployment-" + DEPLOYMENT_ID),
                emitterConsumerCaptor.capture()
        );
        emitterConsumerCaptor.getValue().apply(sseEmitter);

        // Then
        verify(eventReaderFactory).create(DEPLOYMENT_ID, eventTypeConfig);
        verify(eventTypeReader).readEvents(eq(eventSource), any(Consumer.class));
    }

    private Event createTestEvent() {
        var involvedObject = new ObjectReferenceBuilder()
                .withKind(INVOLVED_OBJECT_KIND.name())
                .withName(INVOLVED_OBJECT_NAME)
                .withNamespace(INVOLVED_OBJECT_NAMESPACE)
                .build();

        var metadata = new ObjectMeta();
        metadata.setUid(EVENT_UID);
        metadata.setCreationTimestamp(Instant.now().toString());

        return new EventBuilder()
                .withType(EventType.WARNING.name())
                .withReason("Unhealthy")
                .withMessage("Pod is unhealthy")
                .withInvolvedObject(involvedObject)
                .withMetadata(metadata)
                .withCount(1)
                .build();
    }
}
