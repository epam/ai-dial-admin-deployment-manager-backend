package com.epam.aidial.deployment.manager.functional.tests;

import com.epam.aidial.deployment.manager.functional.utils.FunctionalTestHelper;
import com.epam.aidial.deployment.manager.model.DeploymentMetadata;
import com.epam.aidial.deployment.manager.model.ImageDefinition;
import com.epam.aidial.deployment.manager.model.audit.AuditActivity;
import com.epam.aidial.deployment.manager.model.deployment.CreateDeployment;
import com.epam.aidial.deployment.manager.model.deployment.Deployment;
import com.epam.aidial.deployment.manager.model.page.PageRequestModel;
import com.epam.aidial.deployment.manager.model.page.Sort;
import com.epam.aidial.deployment.manager.model.page.SortDirection;
import com.epam.aidial.deployment.manager.service.GlobalDomainWhitelistService;
import com.epam.aidial.deployment.manager.service.ImageDefinitionService;
import com.epam.aidial.deployment.manager.service.audit.AuditActivityService;
import com.epam.aidial.deployment.manager.service.deployment.DeploymentService;
import com.epam.aidial.deployment.manager.service.security.SecurityClaimsExtractor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

public abstract class GlobalWhitelistRollbackFunctionalTest {

    @Autowired
    private GlobalDomainWhitelistService whitelistService;
    @Autowired
    private DeploymentService deploymentService;
    @Autowired
    private ImageDefinitionService imageDefinitionService;
    @Autowired
    private AuditActivityService auditActivityService;
    @Autowired
    private SecurityClaimsExtractor securityClaimsExtractor;

    @BeforeEach
    void setUp() {
        when(securityClaimsExtractor.getEmail()).thenReturn("whitelist-rollback@test.com");
    }

    @Test
    void shouldRollbackWhitelistToPastRevision() {
        whitelistService.updateDomainWhitelist(List.of("example.com", "ghcr.io"));
        Integer baselineRevision = latestRevisionId();

        whitelistService.updateDomainWhitelist(List.of("evil.example"));
        assertThat(whitelistService.getDomainWhitelist()).containsExactly("evil.example");

        var rolledBack = whitelistService.rollback(baselineRevision);

        assertThat(rolledBack).containsExactlyInAnyOrder("example.com", "ghcr.io");
        assertThat(whitelistService.getDomainWhitelist()).containsExactlyInAnyOrder("example.com", "ghcr.io");
    }

    @Test
    void shouldRollbackAsNoOpWhenAlreadyMatchingTargetRevision() {
        whitelistService.updateDomainWhitelist(List.of("example.com", "ghcr.io"));
        Integer baselineRevision = latestRevisionId();
        Integer revisionsBefore = latestRevisionId();

        var rolledBack = whitelistService.rollback(baselineRevision);

        assertThat(rolledBack).containsExactlyInAnyOrder("example.com", "ghcr.io");
        // No new revision should have been produced for a no-op rollback.
        assertThat(latestRevisionId()).isEqualTo(revisionsBefore);
    }

    @Test
    void shouldRollbackSuccessfully_whenTargetRevisionHasGap() {
        // Revision belongs to a different entity (an image def) → the whitelist was not modified at
        // that revision. Rollback must resolve to the latest applicable whitelist revision (≤ target),
        // matching the snapshot endpoint's lenient semantics.
        whitelistService.updateDomainWhitelist(List.of("example.com", "ghcr.io"));

        ImageDefinition unrelated = FunctionalTestHelper.createInterceptorImageDefinition();
        unrelated.setName("gap-unrelated-image");
        imageDefinitionService.createImageDefinition(unrelated);
        Integer gapRevision = latestRevisionId();

        whitelistService.updateDomainWhitelist(List.of("changed.example"));

        var rolledBack = whitelistService.rollback(gapRevision);

        assertThat(rolledBack).containsExactlyInAnyOrder("example.com", "ghcr.io");
        assertThat(whitelistService.getDomainWhitelist()).containsExactlyInAnyOrder("example.com", "ghcr.io");
    }

    @Test
    void shouldRollbackEmptyWhitelistApplyAsEmptyReplace() {
        // First set non-empty so the next write to empty actually changes the state and
        // produces an audit activity we can address by revision.
        whitelistService.updateDomainWhitelist(List.of("placeholder.example"));

        // Now establish the "empty whitelist" baseline at a known revision.
        whitelistService.updateDomainWhitelist(List.of());
        Integer emptyRevision = latestRevisionId();

        // Mutate to non-empty, then roll back to the empty baseline.
        whitelistService.updateDomainWhitelist(List.of("example.com"));
        assertThat(whitelistService.getDomainWhitelist()).containsExactly("example.com");

        var rolledBack = whitelistService.rollback(emptyRevision);

        assertThat(rolledBack).isEmpty();
        assertThat(whitelistService.getDomainWhitelist()).isEmpty();
    }

    @Test
    void shouldNotModifyDeploymentOrImageDefinitionAllowedDomains_whenRollingBackWhitelist() {
        // FR-005: the global whitelist is a separate scope. Rolling it back must NOT mutate
        // any deployment's `allowedDomains` or any image-definition's `allowedDomains`.
        ImageDefinition imageDef = FunctionalTestHelper.createInterceptorImageDefinition();
        imageDef.setName("whitelist-cascade-image");
        imageDef.setAllowedDomains(List.of("imagedef-domain.com"));
        ImageDefinition image = imageDefinitionService.createImageDefinition(imageDef);
        imageDefinitionService.completeBuildSuccessfully(image.getId(), "test-image", System.currentTimeMillis());

        CreateDeployment depRequest = FunctionalTestHelper.createInterceptorDeploymentRequest(image.getId());
        depRequest.setId("whitelist-cascade-deployment");
        depRequest.setMetadata(new DeploymentMetadata(List.of()));
        depRequest.setAllowedDomains(List.of("deployment-domain.com"));
        Deployment deployment = deploymentService.createDeployment(depRequest);

        whitelistService.updateDomainWhitelist(List.of("example.com"));
        Integer baselineRevision = latestRevisionId();
        whitelistService.updateDomainWhitelist(List.of("changed.example"));

        whitelistService.rollback(baselineRevision);

        ImageDefinition imageAfter = imageDefinitionService.getImageDefinition(image.getId()).orElseThrow();
        Deployment deploymentAfter = deploymentService.getDeployment(deployment.getId()).orElseThrow();
        assertThat(imageAfter.getAllowedDomains()).containsExactly("imagedef-domain.com");
        assertThat(deploymentAfter.getAllowedDomains()).containsExactly("deployment-domain.com");
    }

    private Integer latestRevisionId() {
        PageRequestModel request = new PageRequestModel();
        request.setPageNumber(0);
        request.setPageSize(100);
        Sort sort = new Sort();
        sort.setColumn("epochTimestampMs");
        sort.setDirection(SortDirection.DESC);
        request.setSorts(List.of(sort));
        var activities = auditActivityService.getActivitiesList(request);
        return activities.getData().stream()
                .filter(a -> a.getActivityType() != null)
                .findFirst()
                .map(AuditActivity::getRevision)
                .orElseThrow(() -> new IllegalStateException("No activities found"));
    }
}
