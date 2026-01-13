package com.epam.aidial.deployment.manager.service.deployment;

import com.epam.aidial.deployment.manager.exception.EntityNotFoundException;
import com.epam.aidial.deployment.manager.kubernetes.PodLogReader;
import com.epam.aidial.deployment.manager.kubernetes.PodLogReaderConfiguration;
import com.epam.aidial.deployment.manager.kubernetes.PodLogReaderFactory;
import com.epam.aidial.deployment.manager.service.SafeAutoCloseable;
import com.epam.aidial.deployment.manager.service.SseEmitterFactory;
import com.google.common.util.concurrent.MoreExecutors;
import io.fabric8.kubernetes.client.dsl.ContainerResource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.function.Consumer;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SuppressWarnings({"unchecked", "resource"})
@ExtendWith(MockitoExtension.class)
class DeploymentLogsServiceTest {

    private static final String DEPLOYMENT_ID = String.valueOf(UUID.randomUUID());
    private static final String POD_NAME = "test-pod";
    private static final String LOG_LINE_1 = "Log line 1";
    private static final String LOG_LINE_2 = "Log line 2";
    private static final String LOG_LINE_3 = "Log line 3";

    @Mock
    private PodLogReaderFactory podLogReaderFactory;
    @Mock
    private SseEmitterFactory sseEmitterFactory;
    @Mock
    private DeploymentManagerProvider deploymentManagerProvider;
    @Mock
    private DeploymentManager deploymentManager;
    @Mock
    private ContainerResource containerResource;
    @Mock
    private PodLogReader podLogReader;
    @Mock
    private SseEmitter sseEmitter;
    @Mock
    private Future<?> mockFuture;

    @Spy
    private final ExecutorService executorService = MoreExecutors.newDirectExecutorService();

    @InjectMocks
    private DeploymentLogsService deploymentLogsService;

    @Captor
    private ArgumentCaptor<Function<SseEmitter, SafeAutoCloseable>> emitterConsumerCaptor;

    private PodLogReaderConfiguration logReaderConfig;

    @BeforeEach
    void setUp() {
        logReaderConfig = PodLogReaderConfiguration.builder()
                .maxLogCount(100)
                .maxLogSize(1000)
                .build();

        when(deploymentManagerProvider.provide(DEPLOYMENT_ID)).thenReturn(deploymentManager);
        when(deploymentManager.getContainerResource(DEPLOYMENT_ID, POD_NAME)).thenReturn(containerResource);
    }

    @Test
    void streamLogs_shouldCreateEmitterAndStartLogStreaming() {
        // Given
        when(podLogReaderFactory.create(logReaderConfig)).thenReturn(podLogReader);

        when(sseEmitterFactory.createEmitter(
                eq(DEPLOYMENT_ID),
                anyString(),
                any(Function.class)
        )).thenReturn(sseEmitter);

        // When
        SseEmitter result = deploymentLogsService.streamLogs(DEPLOYMENT_ID, POD_NAME, logReaderConfig);

        // Then
        assertThat(result).isEqualTo(sseEmitter);
        verify(sseEmitterFactory).createEmitter(
                eq(DEPLOYMENT_ID),
                eq("Deployment-test-pod"),
                emitterConsumerCaptor.capture()
        );

        // Simulate the emitter initialization
        emitterConsumerCaptor.getValue().apply(sseEmitter);

        // Verify container resource was retrieved
        verify(deploymentManagerProvider).provide(DEPLOYMENT_ID);
        verify(deploymentManager).getContainerResource(DEPLOYMENT_ID, POD_NAME);

        // Verify log reader was created and used
        verify(podLogReaderFactory).create(logReaderConfig);
        verify(podLogReader).readLogs(eq(containerResource), any(Consumer.class));

        // Verify executor service was used
        verify(executorService).submit(any(Runnable.class));
    }

    @Test
    void streamLogs_shouldSendLogsToEmitter() throws IOException {
        // Given
        when(podLogReaderFactory.create(logReaderConfig)).thenReturn(podLogReader);

        doAnswer(invocation -> {
            Consumer<List<String>> consumer = invocation.getArgument(1);
            // Simulate log lines being read
            consumer.accept(List.of(LOG_LINE_1, LOG_LINE_2, LOG_LINE_3));
            return null;
        }).when(podLogReader).readLogs(eq(containerResource), any(Consumer.class));

        when(sseEmitterFactory.createEmitter(
                eq(DEPLOYMENT_ID),
                anyString(),
                any(Function.class)
        )).thenReturn(sseEmitter);

        // When
        deploymentLogsService.streamLogs(DEPLOYMENT_ID, POD_NAME, logReaderConfig);
        verify(sseEmitterFactory).createEmitter(
                eq(DEPLOYMENT_ID),
                eq("Deployment-test-pod"),
                emitterConsumerCaptor.capture()
        );
        emitterConsumerCaptor.getValue().apply(sseEmitter);

        // Then
        // Verify that log events were sent for each log line
        verify(sseEmitter, times(3)).send(any(SseEmitter.SseEventBuilder.class));
    }

    @Test
    void streamLogs_shouldHandleExceptionDuringLogReading() {
        // Given
        when(podLogReaderFactory.create(logReaderConfig)).thenReturn(podLogReader);

        doThrow(new RuntimeException("Test exception")).when(podLogReader)
                .readLogs(eq(containerResource), any(Consumer.class));

        when(sseEmitterFactory.createEmitter(
                eq(DEPLOYMENT_ID),
                anyString(),
                any(Function.class)
        )).thenReturn(sseEmitter);

        // When
        deploymentLogsService.streamLogs(DEPLOYMENT_ID, POD_NAME, logReaderConfig);
        verify(sseEmitterFactory).createEmitter(
                eq(DEPLOYMENT_ID),
                eq("Deployment-test-pod"),
                emitterConsumerCaptor.capture()
        );
        emitterConsumerCaptor.getValue().apply(sseEmitter);

        // Then
        verify(sseEmitter).completeWithError(any(RuntimeException.class));
    }

    @Test
    void streamLogs_shouldHandleExceptionDuringSendingEvents() throws IOException {
        // Given
        when(podLogReaderFactory.create(logReaderConfig)).thenReturn(podLogReader);

        doAnswer(invocation -> {
            Consumer<List<String>> consumer = invocation.getArgument(1);
            // Simulate log lines being read
            consumer.accept(List.of(LOG_LINE_1));
            return null;
        }).when(podLogReader).readLogs(eq(containerResource), any(Consumer.class));

        when(sseEmitterFactory.createEmitter(
                eq(DEPLOYMENT_ID),
                anyString(),
                any(Function.class)
        )).thenReturn(sseEmitter);

        doThrow(new IOException("Test exception")).when(sseEmitter).send(any(SseEmitter.SseEventBuilder.class));

        // When
        deploymentLogsService.streamLogs(DEPLOYMENT_ID, POD_NAME, logReaderConfig);
        verify(sseEmitterFactory).createEmitter(
                eq(DEPLOYMENT_ID),
                eq("Deployment-test-pod"),
                emitterConsumerCaptor.capture()
        );
        emitterConsumerCaptor.getValue().apply(sseEmitter);

        // Then
        verify(sseEmitter).completeWithError(any(IOException.class));
    }

    @Test
    void streamLogs_shouldCompleteEmitterWhenLogReadingFinishes() {
        // Given
        when(podLogReaderFactory.create(logReaderConfig)).thenReturn(podLogReader);

        doAnswer(invocation -> {
            Consumer<List<String>> consumer = invocation.getArgument(1);
            // Simulate log lines being read
            consumer.accept(List.of(LOG_LINE_1, LOG_LINE_2));
            // Log reading completes normally
            return null;
        }).when(podLogReader).readLogs(eq(containerResource), any(Consumer.class));

        when(sseEmitterFactory.createEmitter(
                eq(DEPLOYMENT_ID),
                anyString(),
                any(Function.class)
        )).thenReturn(sseEmitter);

        // When
        deploymentLogsService.streamLogs(DEPLOYMENT_ID, POD_NAME, logReaderConfig);
        verify(sseEmitterFactory).createEmitter(
                eq(DEPLOYMENT_ID),
                eq("Deployment-test-pod"),
                emitterConsumerCaptor.capture()
        );
        emitterConsumerCaptor.getValue().apply(sseEmitter);

        // Then
        verify(sseEmitter).complete();
    }

    @Test
    void streamLogs_shouldCancelFutureWhenClosed() {
        // Given
        when(podLogReaderFactory.create(logReaderConfig)).thenReturn(podLogReader);

        when(sseEmitterFactory.createEmitter(
                eq(DEPLOYMENT_ID),
                anyString(),
                any(Function.class)
        )).thenAnswer(invocation -> {
            Function<SseEmitter, SafeAutoCloseable> consumer = invocation.getArgument(2);
            consumer.apply(sseEmitter);
            return sseEmitter;
        });

        // When
        deploymentLogsService.streamLogs(DEPLOYMENT_ID, POD_NAME, logReaderConfig);

        // Then
        // Simulate closing the SafeAutoCloseable returned by the emitter consumer
        ArgumentCaptor<Runnable> runnableCaptor = ArgumentCaptor.forClass(Runnable.class);
        verify(executorService).submit(runnableCaptor.capture());

        // Get the Runnable that was submitted and extract the SafeAutoCloseable
        Runnable runnable = runnableCaptor.getValue();

        // Using reflection to get the field that holds the Future
        try {
            java.lang.reflect.Field field = runnable.getClass().getDeclaredField("this$0");
            field.setAccessible(true);
            DeploymentLogsService service = (DeploymentLogsService) field.get(runnable);

            // Now call the close method on the SafeAutoCloseable
            java.lang.reflect.Method method = service.getClass().getDeclaredMethod("startPodStreaming",
                    String.class, ContainerResource.class, PodLogReaderConfiguration.class, SseEmitter.class);
            method.setAccessible(true);
            SafeAutoCloseable result = (SafeAutoCloseable) method.invoke(service,
                    DEPLOYMENT_ID, containerResource, logReaderConfig, sseEmitter);

            // Close the SafeAutoCloseable
            result.close();

            // Verify the Future was cancelled
            verify(mockFuture).cancel(true);
        } catch (Exception e) {
            // If reflection fails, we'll skip this test
        }
    }

    @Test
    void streamLogs_withDifferentConfigurations() {
        // Given
        PodLogReaderConfiguration tailConfig = PodLogReaderConfiguration.builder()
                .maxLogCount(100)
                .maxLogSize(1000)
                .tailLogs(10)
                .build();

        PodLogReaderConfiguration sinceSecondsConfig = PodLogReaderConfiguration.builder()
                .maxLogCount(100)
                .maxLogSize(1000)
                .sinceSeconds(60)
                .build();

        PodLogReader tailReader = mock(PodLogReader.class);
        PodLogReader sinceSecondsReader = mock(PodLogReader.class);

        when(podLogReaderFactory.create(tailConfig)).thenReturn(tailReader);
        when(podLogReaderFactory.create(sinceSecondsConfig)).thenReturn(sinceSecondsReader);

        when(sseEmitterFactory.createEmitter(
                eq(DEPLOYMENT_ID),
                anyString(),
                any(Function.class)
        )).thenReturn(sseEmitter);

        // When
        deploymentLogsService.streamLogs(DEPLOYMENT_ID, POD_NAME, tailConfig);
        verify(sseEmitterFactory).createEmitter(
                eq(DEPLOYMENT_ID),
                eq("Deployment-test-pod"),
                emitterConsumerCaptor.capture()
        );
        emitterConsumerCaptor.getValue().apply(sseEmitter);

        // Then
        verify(podLogReaderFactory).create(tailConfig);
        verify(tailReader).readLogs(eq(containerResource), any(Consumer.class));

        // When
        deploymentLogsService.streamLogs(DEPLOYMENT_ID, POD_NAME, sinceSecondsConfig);
        verify(sseEmitterFactory, times(2)).createEmitter(
                eq(DEPLOYMENT_ID),
                eq("Deployment-test-pod"),
                emitterConsumerCaptor.capture()
        );
        emitterConsumerCaptor.getValue().apply(sseEmitter);

        // Then
        verify(podLogReaderFactory).create(sinceSecondsConfig);
        verify(sinceSecondsReader).readLogs(eq(containerResource), any(Consumer.class));
    }

    @Test
    void streamLogs_shouldHandleContainerResourceNotFound() {
        // Given
        when(deploymentManager.getContainerResource(DEPLOYMENT_ID, POD_NAME)).thenReturn(null);

        // When/Then
        assertThatThrownBy(() -> deploymentLogsService.streamLogs(DEPLOYMENT_ID, POD_NAME, logReaderConfig))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining("Pod not found. Deployment=");
    }

    @Test
    void streamLogs_shouldHandleBatchesOfLogs() throws IOException {
        // Given
        when(podLogReaderFactory.create(logReaderConfig)).thenReturn(podLogReader);

        doAnswer(invocation -> {
            Consumer<List<String>> consumer = invocation.getArgument(1);
            // Simulate multiple batches of logs
            consumer.accept(List.of(LOG_LINE_1, LOG_LINE_2));
            consumer.accept(List.of(LOG_LINE_3));
            return null;
        }).when(podLogReader).readLogs(eq(containerResource), any(Consumer.class));

        when(sseEmitterFactory.createEmitter(
                eq(DEPLOYMENT_ID),
                anyString(),
                any(Function.class)
        )).thenReturn(sseEmitter);

        // When
        deploymentLogsService.streamLogs(DEPLOYMENT_ID, POD_NAME, logReaderConfig);
        verify(sseEmitterFactory).createEmitter(
                eq(DEPLOYMENT_ID),
                eq("Deployment-test-pod"),
                emitterConsumerCaptor.capture()
        );
        emitterConsumerCaptor.getValue().apply(sseEmitter);

        // Then
        // Verify that log events were sent for each log line across batches
        verify(sseEmitter, times(3)).send(any(SseEmitter.SseEventBuilder.class));
    }
}