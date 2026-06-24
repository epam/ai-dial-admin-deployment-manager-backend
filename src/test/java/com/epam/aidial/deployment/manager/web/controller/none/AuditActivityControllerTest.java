package com.epam.aidial.deployment.manager.web.controller.none;

import com.epam.aidial.deployment.manager.configuration.JsonMapperConfiguration;
import com.epam.aidial.deployment.manager.exception.EntityNotFoundException;
import com.epam.aidial.deployment.manager.model.Page;
import com.epam.aidial.deployment.manager.model.audit.ActivityType;
import com.epam.aidial.deployment.manager.model.audit.AuditActivity;
import com.epam.aidial.deployment.manager.model.page.PageRequestModel;
import com.epam.aidial.deployment.manager.service.audit.AuditActivityService;
import com.epam.aidial.deployment.manager.web.controller.AuditActivityController;
import com.epam.aidial.deployment.manager.web.dto.audit.AuditActivityDto;
import com.epam.aidial.deployment.manager.web.mapper.AuditActivityDtoMapper;
import com.epam.aidial.deployment.manager.web.mapper.PageDtoMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AuditActivityController.class)
@Import({JsonMapperConfiguration.class})
class AuditActivityControllerTest extends AbstractControllerNoneSecureTest {

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private AuditActivityService auditActivityService;
    @MockitoBean
    private AuditActivityDtoMapper auditActivityDtoMapper;
    @MockitoBean
    private PageDtoMapper pageDtoMapper;

    @Test
    void listActivities_returnsPagedResults() throws Exception {
        AuditActivity model = new AuditActivity();
        model.setActivityId(UUID.randomUUID());
        model.setActivityType(ActivityType.Create);

        AuditActivityDto dto = new AuditActivityDto();
        dto.setActivityId(model.getActivityId());
        dto.setActivityType("Create");

        when(pageDtoMapper.toPageRequestModel(any())).thenReturn(new PageRequestModel());
        when(auditActivityService.getActivitiesList(any()))
                .thenReturn(Page.<AuditActivity>builder().data(List.of(model)).total(1).totalPages(1).build());
        when(auditActivityDtoMapper.toDto(model)).thenReturn(dto);

        mockMvc.perform(post("/api/v1/activities")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pageNumber\":0,\"pageSize\":10}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.totalPages").value(1))
                .andExpect(jsonPath("$.data[0].activityType").value("Create"));
    }

    @Test
    void getActivity_returnsActivity_whenFound() throws Exception {
        UUID activityId = UUID.randomUUID();
        AuditActivity model = new AuditActivity();
        model.setActivityId(activityId);

        AuditActivityDto dto = new AuditActivityDto();
        dto.setActivityId(activityId);
        dto.setActivityType("Update");

        when(auditActivityService.getActivity(activityId)).thenReturn(model);
        when(auditActivityDtoMapper.toDto(model)).thenReturn(dto);

        mockMvc.perform(get("/api/v1/activities/{activityId}", activityId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activityId").value(activityId.toString()))
                .andExpect(jsonPath("$.activityType").value("Update"));
    }

    @Test
    void getActivity_returns404_whenNotFound() throws Exception {
        UUID activityId = UUID.randomUUID();
        when(auditActivityService.getActivity(activityId))
                .thenThrow(new EntityNotFoundException("Unable to find activity with id " + activityId));

        mockMvc.perform(get("/api/v1/activities/{activityId}", activityId))
                .andExpect(status().isNotFound());
    }

    @Test
    void listActivities_returns400_forInvalidFilterColumn() throws Exception {
        when(pageDtoMapper.toPageRequestModel(any())).thenReturn(new PageRequestModel());
        when(auditActivityService.getActivitiesList(any()))
                .thenThrow(new IllegalArgumentException("Invalid filter column: bad"));

        mockMvc.perform(post("/api/v1/activities")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pageNumber\":0,\"pageSize\":10}"))
                .andExpect(status().isBadRequest());
    }
}
