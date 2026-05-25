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
import com.epam.aidial.deployment.manager.service.audit.HistoryService;
import com.epam.aidial.deployment.manager.service.deployment.DeploymentService;
import com.epam.aidial.deployment.manager.service.security.SecurityClaimsExtractor;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

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
    private HistoryService historyService;
    @Autowired
    private SecurityClaimsExtractor securityClaimsExtractor;
    @Autowired
    private PlatformTransactionManager transactionManager;
    @PersistenceContext
    private EntityManager entityManager;

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
    void shouldRollbackSuccessfully_whenTargetRevisionHasGap() {
        // Revision belongs to a different entity → the subject was not modified at that revision.
        // Rollback must resolve to the latest applicable revision (≤ target) for THIS entity,
        // matching the snapshot endpoint's lenient semantics.
        ImageDefinition subjectDef = FunctionalTestHelper.createInterceptorImageDefinition();
        subjectDef.setName("gap-subject");
        ImageDefinition subjectCreated = imageDefinitionService.createImageDefinition(subjectDef);
        String originalDescription = subjectCreated.getDescription();

        ImageDefinition unrelatedDef = FunctionalTestHelper.createInterceptorImageDefinition();
        unrelatedDef.setName("gap-unrelated");
        ImageDefinition unrelatedCreated = imageDefinitionService.createImageDefinition(unrelatedDef);
        Integer gapRevision = createRevisionFor(unrelatedCreated.getId());

        ImageDefinition mutated = imageDefinitionService.getImageDefinition(subjectCreated.getId()).orElseThrow();
        mutated.setDescription("after-change");
        imageDefinitionService.updateImageDefinition(subjectCreated.getId(), mutated);

        ImageDefinition rolledBack = imageDefinitionService.rollback(subjectCreated.getId(), gapRevision);

        assertThat(rolledBack.getId()).isEqualTo(subjectCreated.getId());
        assertThat(rolledBack.getDescription()).isEqualTo(originalDescription);
    }

    @Test
    void shouldRollbackSuccessfully_whenTargetRevisionIsSequenceAllocatorGap() {
        // Hibernate's pooled sequence allocator burns a block of revinfo ids on every JVM restart,
        // so revinfo legitimately has gap ids that no transaction ever wrote a row for. Simulate
        // the allocator jump by advancing H2's revinfo IDENTITY counter forward by 100, then roll
        // back to an id that falls in the synthesised gap.
        ImageDefinition subjectDef = FunctionalTestHelper.createInterceptorImageDefinition();
        subjectDef.setName("allocator-gap-subject");
        ImageDefinition subjectCreated = imageDefinitionService.createImageDefinition(subjectDef);
        String originalDescription = subjectCreated.getDescription();
        int createMaxRevision = historyService.maxRevisionId();

        int postJumpRevision = createMaxRevision + 100;
        int gapRevision = createMaxRevision + 50;
        advanceRevinfoSequenceTo(postJumpRevision);

        ImageDefinition mutated = imageDefinitionService.getImageDefinition(subjectCreated.getId()).orElseThrow();
        mutated.setDescription("after-change");
        imageDefinitionService.updateImageDefinition(subjectCreated.getId(), mutated);

        assertThat(historyService.maxRevisionId()).isGreaterThanOrEqualTo(postJumpRevision);
        assertThat(gapRevision).isGreaterThan(createMaxRevision);

        ImageDefinition rolledBack = imageDefinitionService.rollback(subjectCreated.getId(), gapRevision);

        assertThat(rolledBack.getId()).isEqualTo(subjectCreated.getId());
        assertThat(rolledBack.getDescription()).isEqualTo(originalDescription);
    }

    @Test
    void shouldFailRollback_whenRevisionIsOutOfRange_andLeaveBuildStatusIntact() {
        // Pre-guard regression: rolling back to an out-of-range revision id used to resolve to the
        // image definition's current state via Envers's lenient lookup and then re-write through
        // updateImageDefinition, spuriously resetting buildStatus to NOT_BUILT. The maxRevisionId()
        // guard must reject the request up front, before the rewrite path runs.
        ImageDefinition imageDef = FunctionalTestHelper.createInterceptorImageDefinition();
        imageDef.setName("out-of-range-subject");
        ImageDefinition created = imageDefinitionService.createImageDefinition(imageDef);
        // Move it out of NOT_BUILT so that a spurious NOT_BUILT reset would be observable.
        imageDefinitionService.startBuild(created.getId());
        imageDefinitionService.failBuild(created.getId(), "test failure");
        ImageDefinition beforeRollback = imageDefinitionService.getImageDefinition(created.getId()).orElseThrow();
        assertThat(beforeRollback.getBuildStatus()).isEqualTo(ImageStatus.BUILD_FAILED);

        assertThatThrownBy(() -> imageDefinitionService.rollback(created.getId(), Integer.MAX_VALUE))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining("revision");

        ImageDefinition afterFailedRollback = imageDefinitionService.getImageDefinition(created.getId()).orElseThrow();
        assertThat(afterFailedRollback.getBuildStatus())
                .as("out-of-range rollback must not reset buildStatus")
                .isEqualTo(ImageStatus.BUILD_FAILED);
        assertThat(afterFailedRollback.getDescription()).isEqualTo(beforeRollback.getDescription());
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

    /**
     * Advances H2's revinfo IDENTITY counter so the next auto-generated revision id is at least
     * {@code nextId}, emulating the jump Hibernate's pooled sequence allocator makes after a JVM
     * restart and leaving an unwritten range below {@code nextId} for gap-id rollback tests.
     */
    private void advanceRevinfoSequenceTo(int nextId) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status ->
                entityManager.createNativeQuery(
                        "ALTER TABLE revinfo ALTER COLUMN id RESTART WITH " + nextId).executeUpdate());
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
