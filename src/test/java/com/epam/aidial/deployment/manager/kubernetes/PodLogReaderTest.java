package com.epam.aidial.deployment.manager.kubernetes;

import io.fabric8.kubernetes.client.dsl.ContainerResource;
import io.fabric8.kubernetes.client.dsl.LogWatch;
import io.fabric8.kubernetes.client.dsl.TimeTailPrettyLoggable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.function.Consumer;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PodLogReaderTest {

    @Mock
    private ContainerResource containerResourceMock;
    @Mock
    private LogWatch logWatchMock;
    @Mock
    private TimeTailPrettyLoggable timeTailPrettyLoggableMock;

    @Mock
    private Consumer<List<String>> consumerMock;

    @Test
    void readLogs_shouldCallTerminated_whenPreviousIsTrue() {
        // Given
        var config = PodLogReaderConfiguration.builder()
                .previous(true)
                .build();
        var reader = new PodLogReader(config);

        when(containerResourceMock.terminated()).thenReturn(timeTailPrettyLoggableMock);
        when(timeTailPrettyLoggableMock.watchLog()).thenReturn(logWatchMock);
        when(logWatchMock.getOutput()).thenReturn(new ByteArrayInputStream("log line".getBytes()));

        // When
        reader.readLogs(containerResourceMock, consumerMock);

        // Then
        verify(containerResourceMock).terminated();
    }

    @Test
    void readLogs_shouldNotCallTerminated_whenPreviousIsFalse() {
        // Given
        var config = PodLogReaderConfiguration.builder()
                .previous(false)
                .build();
        var reader = new PodLogReader(config);

        when(containerResourceMock.watchLog()).thenReturn(logWatchMock);
        when(logWatchMock.getOutput()).thenReturn(new ByteArrayInputStream("log line".getBytes()));

        // When
        reader.readLogs(containerResourceMock, consumerMock);

        // Then
        verify(containerResourceMock).watchLog();
    }
}
