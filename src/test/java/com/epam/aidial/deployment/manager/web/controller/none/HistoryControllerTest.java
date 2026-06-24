package com.epam.aidial.deployment.manager.web.controller.none;

import com.epam.aidial.deployment.manager.configuration.JsonMapperConfiguration;
import com.epam.aidial.deployment.manager.exception.EntityNotFoundException;
import com.epam.aidial.deployment.manager.model.Page;
import com.epam.aidial.deployment.manager.model.audit.AuditRevision;
import com.epam.aidial.deployment.manager.model.page.PageRequestModel;
import com.epam.aidial.deployment.manager.service.audit.HistoryService;
import com.epam.aidial.deployment.manager.web.controller.HistoryController;
import com.epam.aidial.deployment.manager.web.dto.audit.AuditRevisionDto;
import com.epam.aidial.deployment.manager.web.mapper.AuditRevisionDtoMapper;
import com.epam.aidial.deployment.manager.web.mapper.PageDtoMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = HistoryController.class)
@Import({JsonMapperConfiguration.class})
class HistoryControllerTest extends AbstractControllerNoneSecureTest {

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private HistoryService historyService;
    @MockitoBean
    private AuditRevisionDtoMapper auditRevisionDtoMapper;
    @MockitoBean
    private PageDtoMapper pageDtoMapper;

    @Test
    void listRevisions_returnsPagedResults() throws Exception {
        AuditRevision model = new AuditRevision();
        model.setId(1);
        model.setTimestamp(1700000000000L);
        model.setAuthor("testUser");

        AuditRevisionDto dto = new AuditRevisionDto();
        dto.setId(1);
        dto.setTimestamp(1700000000000L);
        dto.setAuthor("testUser");

        when(pageDtoMapper.toPageRequestModel(any())).thenReturn(new PageRequestModel());
        when(historyService.getRevisionsList(any()))
                .thenReturn(Page.<AuditRevision>builder().data(List.of(model)).total(1).totalPages(1).build());
        when(auditRevisionDtoMapper.toDto(model)).thenReturn(dto);

        mockMvc.perform(post("/api/v1/history/revisions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pageNumber\":0,\"pageSize\":10}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.data[0].id").value(1))
                .andExpect(jsonPath("$.data[0].author").value("testUser"));
    }

    @Test
    void queryRevision_byTimestamp_returnsRevision() throws Exception {
        AuditRevision model = new AuditRevision();
        model.setId(5);
        model.setTimestamp(1700000000000L);

        AuditRevisionDto dto = new AuditRevisionDto();
        dto.setId(5);
        dto.setTimestamp(1700000000000L);

        when(historyService.getRevisionByTimestamp(1700000000000L)).thenReturn(model);
        when(auditRevisionDtoMapper.toDto(model)).thenReturn(dto);

        mockMvc.perform(post("/api/v1/history/revisions/query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"GET_BY_TIMESTAMP\",\"timestamp\":1700000000000}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(5))
                .andExpect(jsonPath("$.timestamp").value(1700000000000L));
    }

    @Test
    void queryRevision_byId_returnsRevision() throws Exception {
        AuditRevision model = new AuditRevision();
        model.setId(42);

        AuditRevisionDto dto = new AuditRevisionDto();
        dto.setId(42);

        when(historyService.getRevisionById(42)).thenReturn(model);
        when(auditRevisionDtoMapper.toDto(model)).thenReturn(dto);

        mockMvc.perform(post("/api/v1/history/revisions/query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"GET_BY_ID\",\"id\":42}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(42));
    }

    @Test
    void queryRevision_returns404_whenNotFound() throws Exception {
        when(historyService.getRevisionByTimestamp(999L))
                .thenThrow(new EntityNotFoundException("Unable to find revision at or before timestamp 999"));

        mockMvc.perform(post("/api/v1/history/revisions/query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"GET_BY_TIMESTAMP\",\"timestamp\":999}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void queryRevision_returnsBadRequest_forUnknownType() throws Exception {
        mockMvc.perform(post("/api/v1/history/revisions/query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"UNKNOWN\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void queryRevision_returnsBadRequest_forMissingType() throws Exception {
        mockMvc.perform(post("/api/v1/history/revisions/query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":42}"))
                .andExpect(status().isBadRequest());
    }
}
