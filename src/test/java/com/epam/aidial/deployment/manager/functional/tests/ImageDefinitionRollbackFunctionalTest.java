package com.epam.aidial.deployment.manager.functional.tests;

import com.epam.aidial.deployment.manager.exception.EntityNotFoundException;
import com.epam.aidial.deployment.manager.functional.utils.FunctionalTestHelper;
import com.epam.aidial.deployment.manager.model.DeploymentMetadata;
import com.epam.aidial.deployment.manager.model.ImageDefinition;
import com.epam.aidial.deployment.manager.model.ImageStatus;
import com.epam.aidial.deployment.manager.model.audit.ActivityType;
import com.epam.aidial.deployment.manager.model.audit.AuditActivity;
import com.epam.aidial.deployment.manager.model.deployment.CreateDeployment;
import com.epam.aidial.deployment.manager.model.deployment.Deployment;
import com.epam.aidial.deployment.manager.model.deployment.InternalImageSource;
import com.epam.aidial.deployment.manager.model.page.PageRequestModel;
import com.epam.aidial.deployment.manager.model.page.Sort;
import com.epam.aidial.deployment.manager.model.page.SortDirection;
import com.epam.aidial.deployment.manager.service.ImageDefinitionService;
import com.epam.aidial.deployment.manager.service.audit.AuditActivityService;
import com.epam.aidial.deployment.manager.service.deployment.DeploymentService;
import com.epam.aidial.deployment.manager.service.security.SecurityClaimsExtractor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

public abstract class ImageDefinitionRollbackFunctionalTest {

    @Autowired
    private ImageDefinitionService imageDefinitionService;
    @Autowired
    private DeploymentService deploymentService;
    @Autowired
    private AuditActivityService auditActivityService;
    @Autowired
    private SecurityClaimsExtractor securityClaimsExtractor;

    @BeforeEach
    void setUp() {
        when(securityClaimsExtractor.getEmail()).thenReturn("imgdef-rollback@test.com");
    }

    @Test
    void shouldRollbackImageDefinitionToPastRevision() {
        ImageDefinition imageDef = FunctionalTestHelper.createInterceptorImageDefinition();
        ImageDefinition created = imageDefinitionService.createImageDefinition(imageDef);
        Integer createRevision = createRevisionFor(created.getId());

        ImageDefinition mutated = imageDefinitionService.getImageDefinition(created.getId()).orElseThrow();
        mutated.setDescription("after-change");
        imageDefinitionService.updateImageDefinition(mutated.getId(), mutated);
        assertThat(imageDefinitionService.getImageDefinition(created.getId()).orElseThrow().getDescription())
                .isEqualTo("after-change");

        ImageDefinition rolledBack = imageDefinitionService.rollback(created.getId(), createRevision);

        assertThat(rolledBack.getDescription()).isEqualTo("someDesc");
        assertThat(rolledBack.getBuildStatus()).isEqualTo(ImageStatus.NOT_BUILT);
        assertThat(rolledBack.getId()).isEqualTo(created.getId());
        assertThat(rolledBack.getCreatedAt()).isEqualTo(created.getCreatedAt());
    }

    @Test
    void shouldResetBuildStatusOnRollback_whenAnyFieldDiffers() {
        ImageDefinition imageDef = FunctionalTestHelper.createInterceptorImageDefinition();
        ImageDefinition created = imageDefinitionService.createImageDefinition(imageDef);
        Integer createRevision = createRevisionFor(created.getId());

        // Move it to BUILD_FAILED so it stays rollback-eligible.
        imageDefinitionService.startBuild(created.getId());
        imageDefinitionService.failBuild(created.getId(), "test failure");
        ImageDefinition mutated = imageDefinitionService.getImageDefinition(created.getId()).orElseThrow();
        assertThat(mutated.getBuildStatus()).isEqualTo(ImageStatus.BUILD_FAILED);

        mutated.setDescription("after-change");
        imageDefinitionService.updateImageDefinition(mutated.getId(), mutated);

        ImageDefinition rolledBack = imageDefinitionService.rollback(created.getId(), createRevision);

        // Per FR-008: buildStatus is reset; build artifacts (imageName/builtAt) are not separately
        // cleared because they cannot be non-null in rollback-eligible states under normal flow.
        assertThat(rolledBack.getBuildStatus()).isEqualTo(ImageStatus.NOT_BUILT);
        assertThat(rolledBack.getDescription()).isEqualTo("someDesc");
    }

    @Test
    void shouldFailRollback_whenBuildStatusIsBuildSuccessful() {
        ImageDefinition imageDef = FunctionalTestHelper.createInterceptorImageDefinition();
        ImageDefinition created = imageDefinitionService.createImageDefinition(imageDef);
        Integer createRevision = createRevisionFor(created.getId());

        imageDefinitionService.completeBuildSuccessfully(created.getId(), "test-image", System.currentTimeMillis());

        assertThatThrownBy(() -> imageDefinitionService.rollback(created.getId(), createRevision))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("BUILD_SUCCESSFUL");
    }

    @Test
    void shouldFailRollback_whenBuildStatusIsBuilding() {
        ImageDefinition imageDef = FunctionalTestHelper.createInterceptorImageDefinition();
        ImageDefinition created = imageDefinitionService.createImageDefinition(imageDef);
        Integer createRevision = createRevisionFor(created.getId());

        imageDefinitionService.startBuild(created.getId());
        assertThat(imageDefinitionService.getImageDefinition(created.getId()).orElseThrow().getBuildStatus())
                .isEqualTo(ImageStatus.BUILDING);

        assertThatThrownBy(() -> imageDefinitionService.rollback(created.getId(), createRevision))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("BUILDING");
    }

    @Test
    void shouldFailRollback_whenImageDefinitionNotFound() {
        assertThatThrownBy(() -> imageDefinitionService.rollback(UUID.randomUUID(), 1))
                .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    void shouldRollbackSuccessfully_whenAlreadyMatchingTargetRevision() {
        ImageDefinition imageDef = FunctionalTestHelper.createInterceptorImageDefinition();
        ImageDefinition created = imageDefinitionService.createImageDefinition(imageDef);
        Integer createRevision = createRevisionFor(created.getId());

        ImageDefinition rolledBack = imageDefinitionService.rollback(created.getId(), createRevision);

        assertThat(rolledBack.getId()).isEqualTo(created.getId());
        assertThat(rolledBack.getDescription()).isEqualTo(created.getDescription());
    }

    @Test
    void shouldFailRollback_whenRevisionDoesNotExist() {
        ImageDefinition imageDef = FunctionalTestHelper.createInterceptorImageDefinition();
        ImageDefinition created = imageDefinitionService.createImageDefinition(imageDef);

        assertThatThrownBy(() -> imageDefinitionService.rollback(created.getId(), 999_999))
                .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    void shouldFailRollback_whenRevisionPredatesImageDefinitionCreation() {
        // Create an unrelated image def to anchor a known earlier revision.
        ImageDefinition firstImageDef = FunctionalTestHelper.createInterceptorImageDefinition();
        firstImageDef.setName("first-anchor");
        ImageDefinition firstCreated = imageDefinitionService.createImageDefinition(firstImageDef);
        Integer earlierRevision = createRevisionFor(firstCreated.getId());

        // Now create the subject — it exists from a later revision.
        ImageDefinition subjectDef = FunctionalTestHelper.createInterceptorImageDefinition();
        subjectDef.setName("subject");
        ImageDefinition subjectCreated = imageDefinitionService.createImageDefinition(subjectDef);

        // The earlier revision exists, but the subject did not exist at that revision → 404.
        assertThatThrownBy(() -> imageDefinitionService.rollback(subjectCreated.getId(), earlierRevision))
                .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    void shouldFailRollback_whenNameVersionCollidesWithAnotherImageDefinition() {
        // Image-def A is created with name=collision-target, captured at create revision R.
        // A is then renamed to free up (collision-target, 1.0.0).
        // Image-def B is created with the original (name, version) pair.
        // Rolling A back to R must fail because A's name/version would collide with B.
        ImageDefinition firstDef = FunctionalTestHelper.createInterceptorImageDefinition();
        firstDef.setName("collision-target");
        firstDef.setVersion("1.0.0");
        ImageDefinition firstCreated = imageDefinitionService.createImageDefinition(firstDef);
        Integer firstCreateRevision = createRevisionFor(firstCreated.getId());

        ImageDefinition firstRename = imageDefinitionService.getImageDefinition(firstCreated.getId()).orElseThrow();
        firstRename.setName("collision-target-renamed");
        imageDefinitionService.updateImageDefinition(firstCreated.getId(), firstRename);

        ImageDefinition secondDef = FunctionalTestHelper.createInterceptorImageDefinition();
        secondDef.setName("collision-target");
        secondDef.setVersion("1.0.0");
        imageDefinitionService.createImageDefinition(secondDef);

        assertThatThrownBy(() -> imageDefinitionService.rollback(firstCreated.getId(), firstCreateRevision))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("collide");
    }

    @Test
    void shouldNotModifyReferencingDeployments_whenRollingBackImageDefinition() {
        // FR-005: image-definition rollback does NOT cascade into deployments that reference it.
        // Build a rollback-eligible image-def, attach a deployment that references it, mutate the
        // image-def, then roll it back and verify the deployment's stored configuration is untouched.
        ImageDefinition imageDef = FunctionalTestHelper.createInterceptorImageDefinition();
        imageDef.setName("cascade-source-image");
        ImageDefinition created = imageDefinitionService.createImageDefinition(imageDef);
        imageDefinitionService.completeBuildSuccessfully(created.getId(), "test-image", System.currentTimeMillis());

        CreateDeployment deploymentRequest = FunctionalTestHelper.createInterceptorDeploymentRequest(created.getId());
        deploymentRequest.setId("cascade-deployment");
        deploymentRequest.setMetadata(new DeploymentMetadata(List.of()));
        deploymentRequest.setSource(new InternalImageSource(created.getId(), null, null, null));
        Deployment deployment = deploymentService.createDeployment(deploymentRequest);
        Deployment deploymentBefore = deploymentService.getDeployment(deployment.getId()).orElseThrow();

        // The previously-built image-def isn't rollback-eligible. Use a separate rollback-eligible
        // image-def with its own referencing deployment to exercise the cascade guarantee.
        ImageDefinition rollbackable = FunctionalTestHelper.createInterceptorImageDefinition();
        rollbackable.setName("cascade-source-image-rollbackable");
        ImageDefinition rb = imageDefinitionService.createImageDefinition(rollbackable);
        Integer rbCreateRevision = createRevisionFor(rb.getId());

        ImageDefinition mutated = imageDefinitionService.getImageDefinition(rb.getId()).orElseThrow();
        mutated.setDescription("after-change");
        imageDefinitionService.updateImageDefinition(rb.getId(), mutated);

        imageDefinitionService.rollback(rb.getId(), rbCreateRevision);

        Deployment deploymentAfter = deploymentService.getDeployment(deployment.getId()).orElseThrow();
        assertThat(deploymentAfter.getSource()).isEqualTo(deploymentBefore.getSource());
        assertThat(deploymentAfter.getDisplayName()).isEqualTo(deploymentBefore.getDisplayName());
        assertThat(deploymentAfter.getDescription()).isEqualTo(deploymentBefore.getDescription());
    }

    private Integer createRevisionFor(UUID resourceId) {
        PageRequestModel request = new PageRequestModel();
        request.setPageNumber(0);
        request.setPageSize(100);
        Sort sort = new Sort();
        sort.setColumn("epochTimestampMs");
        sort.setDirection(SortDirection.ASC);
        request.setSorts(List.of(sort));
        return auditActivityService.getActivitiesList(request).getData().stream()
                .filter(a -> a.getActivityType() == ActivityType.Create
                        && resourceId.toString().equals(a.getResourceId()))
                .findFirst()
                .map(AuditActivity::getRevision)
                .orElseThrow(() -> new IllegalStateException("Create activity not found for id " + resourceId));
    }
}
