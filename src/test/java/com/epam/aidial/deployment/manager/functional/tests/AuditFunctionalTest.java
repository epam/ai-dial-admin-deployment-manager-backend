package com.epam.aidial.deployment.manager.functional.tests;

import com.epam.aidial.deployment.manager.functional.utils.FunctionalTestHelper;
import com.epam.aidial.deployment.manager.model.ImageDefinition;
import com.epam.aidial.deployment.manager.model.audit.ActivityType;
import com.epam.aidial.deployment.manager.model.audit.AuditActivity;
import com.epam.aidial.deployment.manager.model.audit.AuditRevision;
import com.epam.aidial.deployment.manager.model.page.PageRequestModel;
import com.epam.aidial.deployment.manager.model.page.Sort;
import com.epam.aidial.deployment.manager.model.page.SortDirection;
import com.epam.aidial.deployment.manager.service.ImageDefinitionService;
import com.epam.aidial.deployment.manager.service.audit.AuditActivityService;
import com.epam.aidial.deployment.manager.service.audit.HistoryService;
import com.epam.aidial.deployment.manager.service.security.SecurityClaimsExtractor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Collection;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

public abstract class AuditFunctionalTest {

    @Autowired
    private ImageDefinitionService imageDefinitionService;
    @Autowired
    private AuditActivityService auditActivityService;
    @Autowired
    private HistoryService historyService;
    @Autowired
    private SecurityClaimsExtractor securityClaimsExtractor;

    @BeforeEach
    void setUpAudit() {
        when(securityClaimsExtractor.getAuthor()).thenReturn("audit-test-user");
        when(securityClaimsExtractor.getEmail()).thenReturn("audit@test.com");
    }

    @Test
    void createImageDefinition_generatesCreateActivity() {
        ImageDefinition imageDef = FunctionalTestHelper.createMcpImageDefinition();
        ImageDefinition created = imageDefinitionService.createImageDefinition(imageDef);

        List<AuditActivity> activities = getAllActivities();

        assertThat(activities).isNotEmpty();
        assertThat(activities).anyMatch(a ->
                a.getActivityType() == ActivityType.Create
                        && a.getResourceId().equals(created.getId().toString())
                        && "audit-test-user".equals(a.getInitiatedAuthor())
                        && "audit@test.com".equals(a.getInitiatedEmail()));
    }

    @Test
    void updateImageDefinition_generatesUpdateActivity() {
        ImageDefinition imageDef = FunctionalTestHelper.createMcpImageDefinition();
        ImageDefinition created = imageDefinitionService.createImageDefinition(imageDef);

        created.setDescription("updated description");
        imageDefinitionService.updateImageDefinition(created.getId(), created);

        List<AuditActivity> activities = getAllActivities();

        assertThat(activities).anyMatch(a ->
                a.getActivityType() == ActivityType.Update
                        && a.getResourceId().equals(created.getId().toString()));
    }

    @Test
    void deleteImageDefinition_generatesDeleteActivity() {
        ImageDefinition imageDef = FunctionalTestHelper.createMcpImageDefinition();
        ImageDefinition created = imageDefinitionService.createImageDefinition(imageDef);

        imageDefinitionService.deleteImageDefinitionSync(created.getId());

        List<AuditActivity> activities = getAllActivities();

        assertThat(activities).anyMatch(a ->
                a.getActivityType() == ActivityType.Delete
                        && a.getResourceId().equals(created.getId().toString()));
    }

    @Test
    void snapshotEndpoint_returnsEntityAtRevision() {
        ImageDefinition imageDef = FunctionalTestHelper.createMcpImageDefinition();
        ImageDefinition created = imageDefinitionService.createImageDefinition(imageDef);
        String originalDescription = created.getDescription();

        // Get the revision from the first activity
        List<AuditActivity> activitiesAfterCreate = getAllActivities();
        AuditActivity createActivity = activitiesAfterCreate.stream()
                .filter(a -> a.getActivityType() == ActivityType.Create
                        && a.getResourceId().equals(created.getId().toString()))
                .findFirst()
                .orElseThrow();
        Integer createRevision = createActivity.getRevision();

        // Update the image definition
        created.setDescription("changed description");
        imageDefinitionService.updateImageDefinition(created.getId(), created);

        // Retrieve snapshot at the create revision
        ImageDefinition snapshot = imageDefinitionService.getImageDefinitionSnapshot(created.getId(), createRevision);

        assertThat(snapshot.getDescription()).isEqualTo(originalDescription);
    }

    @Test
    void revisionByTimestamp_returnsCorrectRevision() {
        long beforeCreate = System.currentTimeMillis();

        ImageDefinition imageDef = FunctionalTestHelper.createMcpImageDefinition();
        imageDefinitionService.createImageDefinition(imageDef);

        // Query for revision at current time — should find the one we just created
        long afterCreate = System.currentTimeMillis();
        AuditRevision revision = historyService.getRevisionByTimestamp(afterCreate);

        assertThat(revision).isNotNull();
        assertThat(revision.getTimestamp()).isGreaterThanOrEqualTo(beforeCreate);
        assertThat(revision.getTimestamp()).isLessThanOrEqualTo(afterCreate);
    }

    private List<AuditActivity> getAllActivities() {
        PageRequestModel request = new PageRequestModel();
        request.setPageNumber(0);
        request.setPageSize(100);
        Sort sort = new Sort();
        sort.setColumn("epochTimestampMs");
        sort.setDirection(SortDirection.ASC);
        request.setSorts(List.of(sort));

        Collection<AuditActivity> data = auditActivityService.getActivitiesList(request).getData();
        return data instanceof List ? (List<AuditActivity>) data : List.copyOf(data);
    }
}
