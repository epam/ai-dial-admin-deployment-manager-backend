package com.epam.aidial.deployment.manager.functional.tests;

import com.epam.aidial.deployment.manager.exception.ImageBuildNotInProgressException;
import com.epam.aidial.deployment.manager.functional.utils.FunctionalTestHelper;
import com.epam.aidial.deployment.manager.model.ImageDefinition;
import com.epam.aidial.deployment.manager.model.ImageStatus;
import com.epam.aidial.deployment.manager.model.audit.ActivityType;
import com.epam.aidial.deployment.manager.model.audit.AuditActivity;
import com.epam.aidial.deployment.manager.model.page.PageRequestModel;
import com.epam.aidial.deployment.manager.model.page.Sort;
import com.epam.aidial.deployment.manager.model.page.SortDirection;
import com.epam.aidial.deployment.manager.service.ImageBuildStopService;
import com.epam.aidial.deployment.manager.service.ImageDefinitionService;
import com.epam.aidial.deployment.manager.service.audit.AuditActivityService;
import com.epam.aidial.deployment.manager.service.security.SecurityClaimsExtractor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

public abstract class StopImageBuildFunctionalTest {

    @Autowired
    private ImageDefinitionService imageDefinitionService;
    @Autowired
    private ImageBuildStopService imageBuildStopService;
    @Autowired
    private AuditActivityService auditActivityService;
    @Autowired
    private SecurityClaimsExtractor securityClaimsExtractor;

    @BeforeEach
    void setUp() {
        when(securityClaimsExtractor.getAuthor()).thenReturn("stop-test-user");
        when(securityClaimsExtractor.getEmail()).thenReturn("stop@test.com");
    }

    @Test
    void shouldStopRunningBuild() {
        ImageDefinition created = imageDefinitionService.createImageDefinition(FunctionalTestHelper.createMcpImageDefinition());
        UUID id = created.getId();
        imageDefinitionService.startBuild(id);

        imageBuildStopService.stopBuild(id);

        ImageDefinition after = imageDefinitionService.getImageDefinition(id).orElseThrow();
        assertThat(after.getBuildStatus()).isEqualTo(ImageStatus.BUILD_STOPPED);
        assertThat(after.getBuildStatus().isFinal()).isTrue();
        assertThat(after.getImageName()).isNull();
        assertThat(after.getBuildLogs()).isNotEmpty();
        assertThat(after.getBuildLogs().getFirst()).contains("Image build started");
    }

    @Test
    void shouldRebuildAfterStop() {
        ImageDefinition created = imageDefinitionService.createImageDefinition(FunctionalTestHelper.createMcpImageDefinition());
        UUID id = created.getId();
        imageDefinitionService.startBuild(id);
        imageBuildStopService.stopBuild(id);

        imageDefinitionService.startBuild(id);

        ImageDefinition after = imageDefinitionService.getImageDefinition(id).orElseThrow();
        assertThat(after.getBuildStatus()).isEqualTo(ImageStatus.BUILDING);
    }

    @ParameterizedTest
    @EnumSource(value = ImageStatus.class, names = {"NOT_BUILT", "BUILD_SUCCESSFUL", "BUILD_FAILED", "BUILD_STOPPED"})
    void shouldFailStopBuild_whenBuildIsNotInProgress(ImageStatus seedStatus) {
        ImageDefinition created = imageDefinitionService.createImageDefinition(FunctionalTestHelper.createMcpImageDefinition());
        UUID id = created.getId();
        seedStatus(id, seedStatus);
        ImageDefinition before = imageDefinitionService.getImageDefinition(id).orElseThrow();

        assertThatThrownBy(() -> imageBuildStopService.stopBuild(id))
                .isInstanceOfSatisfying(ImageBuildNotInProgressException.class, ex ->
                        assertThat(ex.getMessage()).contains(seedStatus.name()));

        ImageDefinition after = imageDefinitionService.getImageDefinition(id).orElseThrow();
        assertThat(after.getBuildStatus()).isEqualTo(before.getBuildStatus());
        assertThat(after.getImageName()).isEqualTo(before.getImageName());
        assertThat(after.getBuiltAt()).isEqualTo(before.getBuiltAt());
        assertThat(after.getBuildLogs()).isEqualTo(before.getBuildLogs());
    }

    @Test
    void shouldRecordAuditActivity_forStop() {
        ImageDefinition created = imageDefinitionService.createImageDefinition(FunctionalTestHelper.createMcpImageDefinition());
        UUID id = created.getId();
        imageDefinitionService.startBuild(id);

        imageBuildStopService.stopBuild(id);

        PageRequestModel request = new PageRequestModel();
        request.setPageNumber(0);
        request.setPageSize(100);
        Sort sort = new Sort();
        sort.setColumn("epochTimestampMs");
        sort.setDirection(SortDirection.ASC);
        request.setSorts(List.of(sort));
        Collection<AuditActivity> activities = auditActivityService.getActivitiesList(request).getData();

        assertThat(activities).anyMatch(a ->
                a.getActivityType() == ActivityType.Update
                        && id.toString().equals(a.getResourceId())
                        && "stop-test-user".equals(a.getInitiatedAuthor()));
    }

    @Test
    void shouldTreatBuildStoppedAsFinalStatus() {
        assertThat(ImageStatus.BUILD_STOPPED.isFinal()).isTrue();
    }

    private void seedStatus(UUID id, ImageStatus status) {
        switch (status) {
            case NOT_BUILT -> {
            }
            case BUILDING -> imageDefinitionService.startBuild(id);
            case BUILD_SUCCESSFUL -> {
                imageDefinitionService.startBuild(id);
                imageDefinitionService.completeBuildSuccessfully(id, "test-image", System.currentTimeMillis());
            }
            case BUILD_FAILED -> {
                imageDefinitionService.startBuild(id);
                imageDefinitionService.failBuild(id, "seeded failure");
            }
            case BUILD_STOPPED -> {
                imageDefinitionService.startBuild(id);
                imageDefinitionService.stopBuild(id);
            }
            default -> throw new IllegalArgumentException("Unsupported seed status: " + status);
        }
    }
}
