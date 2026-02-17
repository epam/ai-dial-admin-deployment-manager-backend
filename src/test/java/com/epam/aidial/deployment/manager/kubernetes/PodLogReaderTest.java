package com.epam.aidial.deployment.manager.kubernetes;

import io.fabric8.kubernetes.client.dsl.ContainerResource;
import io.fabric8.kubernetes.client.dsl.LogWatch;
import io.fabric8.kubernetes.client.dsl.TimeTailPrettyLoggable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PodLogReaderTest {

    @Mock
    private ContainerResource containerResourceMock;
    @Mock
    private LogWatch containerLogWatchMock;
    @Mock
    private LogWatch terminatedLogWatchMock;
    @Mock
    private TimeTailPrettyLoggable terminatedResourceMock;

    @Test
    void readLogs_shouldReadFromTerminatedContainer_whenPreviousIsTrue() {
        // Given
        var config = PodLogReaderConfiguration.builder().previous(true).build();
        var reader = new PodLogReader(config);

        String terminatedLogs = "terminated container log line";
        when(containerResourceMock.terminated()).thenReturn(terminatedResourceMock);
        when(terminatedResourceMock.watchLog()).thenReturn(terminatedLogWatchMock);
        when(terminatedLogWatchMock.getOutput()).thenReturn(new ByteArrayInputStream(terminatedLogs.getBytes()));

        List<String> capturedLogs = new ArrayList<>();
        Consumer<List<String>> consumer = capturedLogs::addAll;

        // When
        reader.readLogs(containerResourceMock, consumer);

        // Then
        verify(containerResourceMock).terminated();
        verify(terminatedResourceMock).watchLog();
        verifyNoInteractions(containerLogWatchMock);
        assertThat(capturedLogs).containsExactly(terminatedLogs);
    }

    @Test
    void readLogs_shouldReadFromCurrentContainer_whenPreviousIsFalse() {
        // Given
        var config = PodLogReaderConfiguration.builder().previous(false).build();
        var reader = new PodLogReader(config);

        String currentLogs = "current container log line";
        when(containerResourceMock.watchLog()).thenReturn(containerLogWatchMock);
        when(containerLogWatchMock.getOutput()).thenReturn(new ByteArrayInputStream(currentLogs.getBytes()));

        List<String> capturedLogs = new ArrayList<>();
        Consumer<List<String>> consumer = capturedLogs::addAll;

        // When
        reader.readLogs(containerResourceMock, consumer);

        // Then
        verify(containerResourceMock).watchLog();
        verifyNoInteractions(terminatedResourceMock);
        assertThat(capturedLogs).containsExactly(currentLogs);
    }
}
