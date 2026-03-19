package com.epam.aidial.deployment.manager.mcpregistry.service;

import com.epam.aidial.deployment.manager.registry.mcp.client.McpRegistryClient;
import com.epam.aidial.deployment.manager.registry.mcp.client.McpRegistryClientException;
import com.epam.aidial.deployment.manager.registry.mcp.model.ServerDetail;
import com.epam.aidial.deployment.manager.registry.mcp.model.ServerListMetadata;
import com.epam.aidial.deployment.manager.registry.mcp.properties.McpRegistryProperties;
import com.epam.aidial.deployment.manager.registry.mcp.service.McpRegistryService;
import com.epam.aidial.deployment.manager.registry.mcp.service.McpServerFilter;
import com.epam.aidial.deployment.manager.registry.mcp.web.dto.ServerFilterDto;
import com.epam.aidial.deployment.manager.registry.mcp.web.dto.ServerListResponseDto;
import com.epam.aidial.deployment.manager.registry.mcp.web.dto.ServerResponseDto;
import com.epam.aidial.deployment.manager.registry.mcp.web.dto.ServersRequestDto;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class McpRegistryServiceTest {

    @Mock
    private McpRegistryClient mcpRegistryClient;

    @Mock
    private McpServerFilter mcpServerFilter;

    @Mock
    private McpRegistryProperties mcpRegistryProperties;

    @InjectMocks
    private McpRegistryService mcpRegistryService;

    private void stubScanLimit(int limit) {
        when(mcpRegistryProperties.getMaxPagesToScan()).thenReturn(limit);
    }

    // --- No filter: pass-through ---

    @Test
    void shouldPassThrough_whenNoFilterApplied() {
        var request = ServersRequestDto.builder().limit(100).build();
        var expected = buildUpstreamResponse(List.of(server("s1")), "cursor1");

        when(mcpServerFilter.hasActiveCriteria(null)).thenReturn(false);
        when(mcpRegistryClient.getServers(request)).thenReturn(expected);

        var result = mcpRegistryService.getServers(request);

        assertThat(result).isEqualTo(expected);
        verify(mcpRegistryClient, times(1)).getServers(any());
    }

    @Test
    void shouldPassThrough_whenFilterHasEmptyCriteria() {
        var filter = ServerFilterDto.builder().build();
        var request = ServersRequestDto.builder().limit(100).filter(filter).build();
        var expected = buildUpstreamResponse(List.of(server("s1")), "cursor1");

        when(mcpServerFilter.hasActiveCriteria(filter)).thenReturn(false);
        when(mcpRegistryClient.getServers(request)).thenReturn(expected);

        var result = mcpRegistryService.getServers(request);

        assertThat(result).isEqualTo(expected);
        verify(mcpRegistryClient, times(1)).getServers(any());
    }

    // --- Single filter dimension ---

    @Test
    void shouldReturnOnlyMatchingServers_whenSingleFilterApplied() {
        var filter = ServerFilterDto.builder().remoteTypes(List.of("sse")).build();
        var request = ServersRequestDto.builder().limit(100).filter(filter).build();
        stubScanLimit(5);

        var s1 = server("s1");
        var s2 = server("s2");
        var upstream = buildUpstreamResponse(List.of(s1, s2), null);

        when(mcpServerFilter.hasActiveCriteria(filter)).thenReturn(true);
        when(mcpRegistryClient.getServers(any())).thenReturn(upstream);
        when(mcpServerFilter.matches(s1, filter)).thenReturn(true);
        when(mcpServerFilter.matches(s2, filter)).thenReturn(false);

        var result = mcpRegistryService.getServers(request);

        assertThat(result.getServers()).containsExactly(s1);
        assertThat(result.getMetadata().getNextCursor()).isNull();
    }

    // --- Multi-page scanning ---

    @Test
    void shouldScanMultiplePages_whenFirstPageHasNoMatches() {
        var filter = ServerFilterDto.builder().remoteTypes(List.of("sse")).build();
        var request = ServersRequestDto.builder().limit(100).filter(filter).build();
        stubScanLimit(5);

        var s1 = server("s1");
        var s2 = server("s2");
        var page1 = buildUpstreamResponse(List.of(s1), "cursor1");
        var page2 = buildUpstreamResponse(List.of(s2), null);

        when(mcpServerFilter.hasActiveCriteria(filter)).thenReturn(true);
        when(mcpRegistryClient.getServers(any())).thenReturn(page1, page2);
        when(mcpServerFilter.matches(s1, filter)).thenReturn(false);
        when(mcpServerFilter.matches(s2, filter)).thenReturn(true);

        var result = mcpRegistryService.getServers(request);

        assertThat(result.getServers()).containsExactly(s2);
        assertThat(result.getMetadata().getNextCursor()).isNull();
        verify(mcpRegistryClient, times(2)).getServers(any());
    }

    @Test
    void shouldCollectAllMatchesFromCurrentPage_whenPageSizeExceeded() {
        var filter = ServerFilterDto.builder().remoteTypes(List.of("sse")).build();
        var request = ServersRequestDto.builder().limit(2).filter(filter).build();
        stubScanLimit(5);

        var s1 = server("s1");
        var s2 = server("s2");
        var s3 = server("s3");
        var page1 = buildUpstreamResponse(List.of(s1, s2, s3), "cursor1");

        when(mcpServerFilter.hasActiveCriteria(filter)).thenReturn(true);
        when(mcpRegistryClient.getServers(any())).thenReturn(page1);
        when(mcpServerFilter.matches(any(), any())).thenReturn(true);

        var result = mcpRegistryService.getServers(request);

        // All matches from the page are collected to avoid losing servers between cursor boundaries
        assertThat(result.getServers()).containsExactly(s1, s2, s3);
        assertThat(result.getMetadata().getNextCursor()).isEqualTo("cursor1");
    }

    // --- Upstream search param forwarding ---

    @Test
    void shouldForwardSearchParam_whenBackendFilterAlsoActive() {
        var filter = ServerFilterDto.builder().remoteTypes(List.of("sse")).build();
        var request = ServersRequestDto.builder().limit(100).search("myserver").filter(filter).build();
        stubScanLimit(5);

        var s1 = server("s1");
        var upstream = buildUpstreamResponse(List.of(s1), null);

        when(mcpServerFilter.hasActiveCriteria(filter)).thenReturn(true);
        when(mcpRegistryClient.getServers(any())).thenReturn(upstream);
        when(mcpServerFilter.matches(s1, filter)).thenReturn(true);

        mcpRegistryService.getServers(request);

        var captor = org.mockito.ArgumentCaptor.forClass(ServersRequestDto.class);
        verify(mcpRegistryClient).getServers(captor.capture());
        assertThat(captor.getValue().getSearch()).isEqualTo("myserver");
        assertThat(captor.getValue().getFilter()).isNull();
    }

    // --- Error handling ---

    @Test
    void shouldReturnPartialResults_whenErrorMidScanWithCollectedResults() {
        var filter = ServerFilterDto.builder().remoteTypes(List.of("sse")).build();
        var request = ServersRequestDto.builder().limit(100).filter(filter).build();
        stubScanLimit(5);

        var s1 = server("s1");
        var page1 = buildUpstreamResponse(List.of(s1), "cursor1");

        when(mcpServerFilter.hasActiveCriteria(filter)).thenReturn(true);
        when(mcpRegistryClient.getServers(any()))
                .thenReturn(page1)
                .thenThrow(new McpRegistryClientException("Upstream error", 502));
        when(mcpServerFilter.matches(s1, filter)).thenReturn(true);

        var result = mcpRegistryService.getServers(request);

        assertThat(result.getServers()).containsExactly(s1);
        assertThat(result.getMetadata().getNextCursor()).isEqualTo("cursor1");
    }

    @Test
    void shouldPropagateException_whenErrorMidScanWithNoResults() {
        var filter = ServerFilterDto.builder().remoteTypes(List.of("sse")).build();
        var request = ServersRequestDto.builder().limit(100).filter(filter).build();
        stubScanLimit(5);

        var s1 = server("s1");
        var page1 = buildUpstreamResponse(List.of(s1), "cursor1");

        when(mcpServerFilter.hasActiveCriteria(filter)).thenReturn(true);
        when(mcpRegistryClient.getServers(any()))
                .thenReturn(page1)
                .thenThrow(new McpRegistryClientException("Upstream error", 502));
        when(mcpServerFilter.matches(s1, filter)).thenReturn(false);

        assertThatThrownBy(() -> mcpRegistryService.getServers(request))
                .isInstanceOf(McpRegistryClientException.class);
    }

    @Test
    void shouldPropagateException_whenFirstPageFails() {
        var filter = ServerFilterDto.builder().remoteTypes(List.of("sse")).build();
        var request = ServersRequestDto.builder().limit(100).filter(filter).build();
        stubScanLimit(5);

        when(mcpServerFilter.hasActiveCriteria(filter)).thenReturn(true);
        when(mcpRegistryClient.getServers(any()))
                .thenThrow(new McpRegistryClientException("Upstream error", 502));

        assertThatThrownBy(() -> mcpRegistryService.getServers(request))
                .isInstanceOf(McpRegistryClientException.class);
    }

    // --- AND logic across dimensions ---

    @Test
    void shouldApplyAndLogicAcrossFilterDimensions() {
        var filter = ServerFilterDto.builder()
                .remoteTypes(List.of("sse"))
                .packageRegistryTypes(List.of("npm"))
                .build();
        var request = ServersRequestDto.builder().limit(100).filter(filter).build();
        stubScanLimit(5);

        var s1 = server("s1"); // matches both
        var s2 = server("s2"); // matches only remote
        var upstream = buildUpstreamResponse(List.of(s1, s2), null);

        when(mcpServerFilter.hasActiveCriteria(filter)).thenReturn(true);
        when(mcpRegistryClient.getServers(any())).thenReturn(upstream);
        when(mcpServerFilter.matches(s1, filter)).thenReturn(true);
        when(mcpServerFilter.matches(s2, filter)).thenReturn(false);

        var result = mcpRegistryService.getServers(request);

        assertThat(result.getServers()).containsExactly(s1);
    }

    // --- Pagination (Phase 4 — T011) ---

    @Test
    void shouldReturnNextCursor_whenScanLimitReachedWithUpstreamRemaining() {
        var filter = ServerFilterDto.builder().remoteTypes(List.of("sse")).build();
        var request = ServersRequestDto.builder().limit(100).filter(filter).build();
        stubScanLimit(2);

        var s1 = server("s1");
        var s2 = server("s2");
        var page1 = buildUpstreamResponse(List.of(s1), "cursor1");
        var page2 = buildUpstreamResponse(List.of(s2), "cursor2");

        when(mcpServerFilter.hasActiveCriteria(filter)).thenReturn(true);
        when(mcpRegistryClient.getServers(any())).thenReturn(page1, page2);
        when(mcpServerFilter.matches(s1, filter)).thenReturn(true);
        when(mcpServerFilter.matches(s2, filter)).thenReturn(true);

        var result = mcpRegistryService.getServers(request);

        assertThat(result.getServers()).containsExactly(s1, s2);
        assertThat(result.getMetadata().getNextCursor()).isEqualTo("cursor2");
    }

    @Test
    void shouldResumeScanningFromCursor() {
        var filter = ServerFilterDto.builder().remoteTypes(List.of("sse")).build();
        var request = ServersRequestDto.builder().limit(100).cursor("cursor1").filter(filter).build();
        stubScanLimit(5);

        var s1 = server("s1");
        var upstream = buildUpstreamResponse(List.of(s1), null);

        when(mcpServerFilter.hasActiveCriteria(filter)).thenReturn(true);
        when(mcpRegistryClient.getServers(any())).thenReturn(upstream);
        when(mcpServerFilter.matches(s1, filter)).thenReturn(true);

        var result = mcpRegistryService.getServers(request);

        var captor = org.mockito.ArgumentCaptor.forClass(ServersRequestDto.class);
        verify(mcpRegistryClient).getServers(captor.capture());
        assertThat(captor.getValue().getCursor()).isEqualTo("cursor1");
        assertThat(result.getServers()).containsExactly(s1);
        assertThat(result.getMetadata().getNextCursor()).isNull();
    }

    @Test
    void shouldReturnNullCursor_whenUpstreamExhausted() {
        var filter = ServerFilterDto.builder().remoteTypes(List.of("sse")).build();
        var request = ServersRequestDto.builder().limit(100).filter(filter).build();
        stubScanLimit(5);

        var s1 = server("s1");
        var upstream = buildUpstreamResponse(List.of(s1), null);

        when(mcpServerFilter.hasActiveCriteria(filter)).thenReturn(true);
        when(mcpRegistryClient.getServers(any())).thenReturn(upstream);
        when(mcpServerFilter.matches(s1, filter)).thenReturn(true);

        var result = mcpRegistryService.getServers(request);

        assertThat(result.getMetadata().getNextCursor()).isNull();
    }

    @Test
    void shouldReturnPartialPage_whenScanLimitHit() {
        var filter = ServerFilterDto.builder().remoteTypes(List.of("sse")).build();
        var request = ServersRequestDto.builder().limit(10).filter(filter).build();
        stubScanLimit(1);

        var s1 = server("s1");
        var s2 = server("s2");
        var page1 = buildUpstreamResponse(List.of(s1, s2), "cursor1");

        when(mcpServerFilter.hasActiveCriteria(filter)).thenReturn(true);
        when(mcpRegistryClient.getServers(any())).thenReturn(page1);
        when(mcpServerFilter.matches(s1, filter)).thenReturn(true);
        when(mcpServerFilter.matches(s2, filter)).thenReturn(false);

        var result = mcpRegistryService.getServers(request);

        assertThat(result.getServers()).hasSize(1);
        assertThat(result.getMetadata().getNextCursor()).isEqualTo("cursor1");
    }

    // --- Scan limit (Phase 5 — T013) ---

    @Test
    void shouldStopAfterConfiguredScanLimit() {
        var filter = ServerFilterDto.builder().remoteTypes(List.of("sse")).build();
        var request = ServersRequestDto.builder().limit(100).filter(filter).build();
        stubScanLimit(2);

        var page1 = buildUpstreamResponse(List.of(server("s1")), "cursor1");
        var page2 = buildUpstreamResponse(List.of(server("s2")), "cursor2");

        when(mcpServerFilter.hasActiveCriteria(filter)).thenReturn(true);
        when(mcpRegistryClient.getServers(any())).thenReturn(page1, page2);
        when(mcpServerFilter.matches(any(), any())).thenReturn(false);

        var result = mcpRegistryService.getServers(request);

        verify(mcpRegistryClient, times(2)).getServers(any());
        assertThat(result.getServers()).isEmpty();
        assertThat(result.getMetadata().getNextCursor()).isEqualTo("cursor2");
    }

    @Test
    void shouldFetchExactlyOnePage_whenScanLimitIsOne() {
        var filter = ServerFilterDto.builder().remoteTypes(List.of("sse")).build();
        var request = ServersRequestDto.builder().limit(100).filter(filter).build();
        stubScanLimit(1);

        var page1 = buildUpstreamResponse(List.of(server("s1")), "cursor1");

        when(mcpServerFilter.hasActiveCriteria(filter)).thenReturn(true);
        when(mcpRegistryClient.getServers(any())).thenReturn(page1);
        when(mcpServerFilter.matches(any(), any())).thenReturn(false);

        mcpRegistryService.getServers(request);

        verify(mcpRegistryClient, times(1)).getServers(any());
    }

    // --- Helpers ---

    private static ServerResponseDto server(String name) {
        return ServerResponseDto.builder()
                .server(ServerDetail.builder().name(name).version("1.0.0").build())
                .build();
    }

    private static ServerListResponseDto buildUpstreamResponse(List<ServerResponseDto> servers, String nextCursor) {
        return ServerListResponseDto.builder()
                .servers(servers)
                .metadata(ServerListMetadata.builder()
                        .nextCursor(nextCursor)
                        .count(servers.size())
                        .build())
                .build();
    }
}
