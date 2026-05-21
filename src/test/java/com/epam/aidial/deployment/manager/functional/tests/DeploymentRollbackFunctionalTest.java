package com.epam.aidial.deployment.manager.functional.tests;

import com.epam.aidial.deployment.manager.dao.repository.DeploymentRepository;
import com.epam.aidial.deployment.manager.exception.EntityNotFoundException;
import com.epam.aidial.deployment.manager.functional.utils.FunctionalTestHelper;
import com.epam.aidial.deployment.manager.model.DeploymentMetadata;
import com.epam.aidial.deployment.manager.model.DeploymentStatus;
import com.epam.aidial.deployment.manager.model.EnvVar;
import com.epam.aidial.deployment.manager.model.EnvVarDefinition;
import com.epam.aidial.deployment.manager.model.EnvVarMountType;
import com.epam.aidial.deployment.manager.model.ImageDefinition;
import com.epam.aidial.deployment.manager.model.ImageType;
import com.epam.aidial.deployment.manager.model.SensitiveEnvVar;
import com.epam.aidial.deployment.manager.model.SimpleEnvVar;
import com.epam.aidial.deployment.manager.model.SimpleEnvVarValue;
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
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

public abstract class DeploymentRollbackFunctionalTest {

    @Autowired
    private DeploymentService deploymentService;
    @Autowired
    private DeploymentRepository deploymentRepository;
    @Autowired
    private ImageDefinitionService imageDefinitionService;
    @Autowired
    private AuditActivityService auditActivityService;
    @Autowired
    private SecurityClaimsExtractor securityClaimsExtractor;
    @Autowired
    private KubernetesClient kubernetesClient;

    private UUID imageDefinitionId;

    @SuppressWarnings({"unchecked", "rawtypes"})
    @BeforeEach
    void setUp() {
        when(securityClaimsExtractor.getEmail()).thenReturn("deployment-rollback@test.com");

        ImageDefinition imageDef = FunctionalTestHelper.createInterceptorImageDefinition();
        ImageDefinition createdImageDef = imageDefinitionService.createImageDefinition(imageDef);
        imageDefinitionId = createdImageDef.getId();
        imageDefinitionService.completeBuildSuccessfully(imageDefinitionId, "test-image", System.currentTimeMillis());

        // Dynamic K8s secret stub: each withName(secretName) returns a Secret whose metadata.name
        // matches the requested name, so AbstractDeploymentManager.resolveSecrets keys correctly
        // when several distinct secret names are in play across create + update + rollback flow.
        Map<String, String> secretData = Map.of(
                "secret-a", "dmFsLWE=",
                "secret-b", "dmFsLWI=");
        MixedOperation mixedOperation = Mockito.mock(MixedOperation.class);
        when(mixedOperation.inNamespace(any())).thenReturn(mixedOperation);
        when(mixedOperation.resource(any())).thenAnswer(inv -> {
            Resource resource = Mockito.mock(Resource.class);
            Secret secret = Mockito.mock(Secret.class);
            ObjectMeta meta = Mockito.mock(ObjectMeta.class);
            when(meta.getName()).thenReturn("created-secret");
            when(secret.getMetadata()).thenReturn(meta);
            when(secret.getData()).thenReturn(secretData);
            when(resource.create()).thenReturn(secret);
            when(resource.get()).thenReturn(secret);
            return resource;
        });
        when(mixedOperation.withName(any())).thenAnswer(inv -> {
            String name = inv.getArgument(0);
            Resource resource = Mockito.mock(Resource.class);
            Secret secret = Mockito.mock(Secret.class);
            ObjectMeta meta = Mockito.mock(ObjectMeta.class);
            when(meta.getName()).thenReturn(name);
            when(secret.getMetadata()).thenReturn(meta);
            when(secret.getData()).thenReturn(secretData);
            when(resource.create()).thenReturn(secret);
            when(resource.get()).thenReturn(secret);
            return resource;
        });
        when(kubernetesClient.secrets()).thenReturn(mixedOperation);
    }

    @Test
    void shouldRollbackDeploymentToPastRevision() {
        CreateDeployment request = createInterceptorDeploymentWithoutSecrets("rollback-test-1");
        Deployment created = deploymentService.createDeployment(request);
        Integer createRevision = createRevisionFor(created.getId());
        String originalDisplayName = created.getDisplayName();

        CreateDeployment mutated = createInterceptorDeploymentWithoutSecrets("rollback-test-1");
        mutated.setDisplayName("changed-display-name");
        deploymentService.updateDeployment(created.getId(), mutated);
        assertThat(deploymentService.getDeployment(created.getId()).orElseThrow().getDisplayName())
                .isEqualTo("changed-display-name");

        Deployment rolledBack = deploymentService.rollback(created.getId(), createRevision);

        assertThat(rolledBack.getDisplayName()).isEqualTo(originalDisplayName);
        assertThat(rolledBack.getId()).isEqualTo(created.getId());
        assertThat(rolledBack.getCreatedAt()).isEqualTo(created.getCreatedAt());
    }

    @Test
    void shouldFailRollback_whenDeploymentIsInActiveState() {
        CreateDeployment request = createInterceptorDeploymentWithoutSecrets("rollback-test-2");
        Deployment created = deploymentService.createDeployment(request);
        Integer createRevision = createRevisionFor(created.getId());

        // Deliberate test backdoor: production flows reach RUNNING via the deployment manager,
        // not via repository.update. We bypass that here to exercise the service-level active-state
        // guard directly. If the repository ever gains a status-transition guard, this test will
        // need to switch to a manager-driven setup instead of failing for the wrong reason.
        Deployment loaded = deploymentRepository.getById(created.getId()).orElseThrow();
        loaded.setStatus(DeploymentStatus.RUNNING);
        deploymentRepository.update(created.getId(), loaded);

        assertThatThrownBy(() -> deploymentService.rollback(created.getId(), createRevision))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("active state");
    }

    @Test
    void shouldFailRollback_whenDeploymentNotFound() {
        assertThatThrownBy(() -> deploymentService.rollback("not-existing", 1))
                .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    void shouldRollbackSuccessfully_whenAlreadyMatchingTargetRevision() {
        CreateDeployment request = createInterceptorDeploymentWithoutSecrets("rollback-test-3");
        Deployment created = deploymentService.createDeployment(request);
        Integer createRevision = createRevisionFor(created.getId());

        Deployment rolledBack = deploymentService.rollback(created.getId(), createRevision);

        assertThat(rolledBack.getId()).isEqualTo(created.getId());
        assertThat(rolledBack.getDisplayName()).isEqualTo(created.getDisplayName());
    }

    @Test
    void shouldFailRollback_whenRevisionDoesNotExist() {
        CreateDeployment request = createInterceptorDeploymentWithoutSecrets("rollback-test-4");
        deploymentService.createDeployment(request);

        assertThatThrownBy(() -> deploymentService.rollback("rollback-test-4", 999_999))
                .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    void shouldFailRollback_whenRevisionPredatesDeploymentCreation() {
        // Anchor a known earlier revision by creating an unrelated image definition first;
        // its create activity gives us a revision id we know exists but predates the subject deployment.
        ImageDefinition anchorImageDef = FunctionalTestHelper.createInterceptorImageDefinition();
        anchorImageDef.setName("predates-anchor-image");
        ImageDefinition anchorCreated = imageDefinitionService.createImageDefinition(anchorImageDef);
        Integer earlierRevision = createRevisionForUuid(anchorCreated.getId());

        // Now create the subject deployment — it exists from a later revision.
        CreateDeployment request = createInterceptorDeploymentWithoutSecrets("rollback-test-5");
        deploymentService.createDeployment(request);

        // Discriminator vs R4 (unknown revision): the predates-creation path goes through
        // entitySnapshotAtRevision and includes "at revision" in its message, whereas the
        // unknown-revision path stops at getRevisionById and reports "revision with id".
        assertThatThrownBy(() -> deploymentService.rollback("rollback-test-5", earlierRevision))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining("at revision");
    }

    @Test
    void shouldFailRollback_whenReferencedImageDefinitionNoLongerExists() {
        // Create a dedicated image-def the deployment will reference, then orphan it after capturing
        // the create revision. The snapshot at that revision points at the deleted image-def, so the
        // rollback path's existence check (FR-003(d)) must fire.
        ImageDefinition orphanable = FunctionalTestHelper.createInterceptorImageDefinition();
        orphanable.setName("orphanable-image");
        ImageDefinition orphanableCreated = imageDefinitionService.createImageDefinition(orphanable);
        UUID orphanableId = orphanableCreated.getId();
        imageDefinitionService.completeBuildSuccessfully(orphanableId, "orphanable-image-built", System.currentTimeMillis());

        CreateDeployment request = createInterceptorDeploymentWithoutSecrets("rollback-test-dangling");
        request.setSource(new InternalImageSource(orphanableId, null, null, null));
        Deployment created = deploymentService.createDeployment(request);
        Integer createRevision = createRevisionFor(created.getId());

        // Repoint the deployment at the standard image-def so the orphanable one can be deleted.
        CreateDeployment repoint = createInterceptorDeploymentWithoutSecrets("rollback-test-dangling");
        repoint.setSource(new InternalImageSource(imageDefinitionId, null, null, null));
        deploymentService.updateDeployment(created.getId(), repoint);

        imageDefinitionService.deleteImageDefinitionSync(orphanableId);

        assertThatThrownBy(() -> deploymentService.rollback(created.getId(), createRevision))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(orphanableId.toString());
    }

    @Test
    void shouldNotModifyReferencedImageDefinition_whenRollingBackDeployment() {
        // FR-005: rolling back a deployment never cascades into the image-def it references.
        ImageDefinition imageBefore = imageDefinitionService.getImageDefinition(imageDefinitionId).orElseThrow();

        CreateDeployment request = createInterceptorDeploymentWithoutSecrets("rollback-test-cascade-image");
        Deployment created = deploymentService.createDeployment(request);
        Integer createRevision = createRevisionFor(created.getId());

        CreateDeployment mutated = createInterceptorDeploymentWithoutSecrets("rollback-test-cascade-image");
        mutated.setDisplayName("touched-display");
        deploymentService.updateDeployment(created.getId(), mutated);

        deploymentService.rollback(created.getId(), createRevision);

        ImageDefinition imageAfter = imageDefinitionService.getImageDefinition(imageDefinitionId).orElseThrow();
        assertThat(imageAfter.getName()).isEqualTo(imageBefore.getName());
        assertThat(imageAfter.getVersion()).isEqualTo(imageBefore.getVersion());
        assertThat(imageAfter.getDescription()).isEqualTo(imageBefore.getDescription());
        assertThat(imageAfter.getAllowedDomains()).isEqualTo(imageBefore.getAllowedDomains());
    }

    @Test
    void shouldNormalizeSourceNameAndVersion_whenImageDefinitionWasRenamed() {
        // Create deployment against an image-def, capture revision, then rename the image-def
        // (which leaves the snapshot's source carrying the old name/version embedded). After
        // rollback, the source's name/version must reflect the *current* image-def, not the
        // stale snapshot values — mirroring how the regular create/update path resolves it.
        ImageDefinition imageDef = FunctionalTestHelper.createInterceptorImageDefinition();
        imageDef.setName("rename-source");
        imageDef.setVersion("1.0.0");
        ImageDefinition renamable = imageDefinitionService.createImageDefinition(imageDef);
        UUID renamableId = renamable.getId();

        CreateDeployment request = createInterceptorDeploymentWithoutSecrets("rollback-test-normalize");
        request.setSource(new InternalImageSource(renamableId, null, null, null));
        Deployment created = deploymentService.createDeployment(request);
        Integer createRevision = createRevisionFor(created.getId());

        // Rename + version bump while still in NOT_BUILT so the update path is taken.
        ImageDefinition renamed = imageDefinitionService.getImageDefinition(renamableId).orElseThrow();
        renamed.setName("rename-source-after");
        renamed.setVersion("2.0.0");
        imageDefinitionService.updateImageDefinition(renamableId, renamed);

        // Mutate the deployment so rollback has something to revert.
        CreateDeployment mutated = createInterceptorDeploymentWithoutSecrets("rollback-test-normalize");
        mutated.setSource(new InternalImageSource(renamableId, null, null, null));
        mutated.setDisplayName("touched");
        deploymentService.updateDeployment(created.getId(), mutated);

        Deployment rolledBack = deploymentService.rollback(created.getId(), createRevision);

        assertThat(rolledBack.getSource()).isInstanceOf(InternalImageSource.class);
        InternalImageSource source = (InternalImageSource) rolledBack.getSource();
        assertThat(source.imageDefinitionId()).isEqualTo(renamableId);
        assertThat(source.imageDefinitionType()).isEqualTo(ImageType.INTERCEPTOR);
        assertThat(source.imageDefinitionName()).isEqualTo("rename-source-after");
        assertThat(source.imageDefinitionVersion()).isEqualTo("2.0.0");
    }

    @Test
    void shouldReprovisionEnvSecret_andResetSensitiveValuesToNull_onRollback() {
        // R1: simple-a + secret-a (sensitive).
        CreateDeployment request = createDeploymentWithEnvs("rollback-test-envs-differ", List.of(
                envSimple("simple-a", "v1"),
                envSensitive("secret-a", "vA")));
        Deployment created = deploymentService.createDeployment(request);
        Integer createRevision = createRevisionFor(created.getId());
        String originalSecretName = sensitiveSecretName(created, "secret-a");
        assertThat(originalSecretName).isNotBlank();

        // Update: structurally different (adds secret-b).
        CreateDeployment mutated = createDeploymentWithEnvs("rollback-test-envs-differ", List.of(
                envSimple("simple-a", "v1"),
                envSensitive("secret-a", "vA"),
                envSensitive("secret-b", "vB")));
        Deployment updated = deploymentService.updateDeployment(created.getId(), mutated);
        String liveSecretName = sensitiveSecretName(updated, "secret-a");
        assertThat(liveSecretName).isNotBlank().isNotEqualTo(originalSecretName);

        Deployment rolledBack = deploymentService.rollback(created.getId(), createRevision);

        assertThat(rolledBack.getEnvs()).hasSize(2);
        assertThat(rolledBack.getEnvs().stream().map(EnvVar::getName))
                .containsExactlyInAnyOrder("simple-a", "secret-a");
        String rolledBackSecretName = sensitiveSecretName(rolledBack, "secret-a");
        assertThat(rolledBackSecretName)
                .as("rollback must reprovision a fresh K8s envs secret distinct from both the snapshot's and the live secret")
                .isNotBlank()
                .isNotEqualTo(originalSecretName)
                .isNotEqualTo(liveSecretName);
        // Sensitive values are never round-tripped from DB; rolled-back entry has a null value.
        SensitiveEnvVar restoredSecret = (SensitiveEnvVar) rolledBack.getEnvs().stream()
                .filter(e -> e.getName().equals("secret-a"))
                .findFirst().orElseThrow();
        assertThat(restoredSecret.getValue()).isNull();
    }

    @Test
    void shouldReprovisionEnvSecret_evenWhenSnapshotStructureMatchesLive() {
        // Verifies rollback always reprovisions (no diff-check gate). If we skipped reprovision when
        // the snapshot structure matched the live structure, we'd silently retain the live sensitive
        // values across what is meant to be a rollback — which would misrepresent the snapshot the
        // operator asked to restore.
        CreateDeployment request = createDeploymentWithEnvs("rollback-test-envs-no-diff", List.of(
                envSensitive("secret-a", "vA")));
        Deployment created = deploymentService.createDeployment(request);
        Integer createRevision = createRevisionFor(created.getId());
        String originalSecretName = sensitiveSecretName(created, "secret-a");

        // Update changes the sensitive value but keeps the same env-var structure.
        Deployment updated = deploymentService.updateDeployment(created.getId(),
                createDeploymentWithEnvs("rollback-test-envs-no-diff", List.of(
                        envSensitive("secret-a", "vA-new"))));
        String liveSecretName = sensitiveSecretName(updated, "secret-a");
        assertThat(liveSecretName).isNotBlank().isNotEqualTo(originalSecretName);

        Deployment rolledBack = deploymentService.rollback(created.getId(), createRevision);

        assertThat(rolledBack.getEnvs()).hasSize(1);
        String rolledBackSecretName = sensitiveSecretName(rolledBack, "secret-a");
        assertThat(rolledBackSecretName)
                .as("rollback must reprovision unconditionally, not realign to the live secret")
                .isNotBlank()
                .isNotEqualTo(originalSecretName)
                .isNotEqualTo(liveSecretName);
        SensitiveEnvVar restored = (SensitiveEnvVar) rolledBack.getEnvs().getFirst();
        assertThat(restored.getValue()).isNull();
    }

    @Test
    void shouldPersistSimpleEnvValueFromSnapshot_onRollback() {
        // Simple env values are persisted to the audit table (only sensitive ones are stripped),
        // so rollback must restore simple values verbatim from the snapshot.
        CreateDeployment request = createDeploymentWithEnvs("rollback-test-envs-simple", List.of(
                envSimple("simple-a", "v1-original")));
        Deployment created = deploymentService.createDeployment(request);
        Integer createRevision = createRevisionFor(created.getId());

        deploymentService.updateDeployment(created.getId(), createDeploymentWithEnvs(
                "rollback-test-envs-simple", List.of(
                        envSimple("simple-a", "v2-changed"))));

        Deployment rolledBack = deploymentService.rollback(created.getId(), createRevision);

        assertThat(rolledBack.getEnvs()).hasSize(1);
        assertThat(rolledBack.getEnvs().getFirst()).isInstanceOf(SimpleEnvVar.class);
        assertThat(rolledBack.getEnvs().getFirst().getValue().getValue()).isEqualTo("v1-original");
    }

    private CreateDeployment createInterceptorDeploymentWithoutSecrets(String id) {
        CreateDeployment request = FunctionalTestHelper.createInterceptorDeploymentRequest(imageDefinitionId);
        request.setId(id);
        request.setMetadata(new DeploymentMetadata(List.of()));
        return request;
    }

    private CreateDeployment createDeploymentWithEnvs(String id, List<EnvVarDefinition> envs) {
        CreateDeployment request = FunctionalTestHelper.createInterceptorDeploymentRequest(imageDefinitionId);
        request.setId(id);
        request.setMetadata(new DeploymentMetadata(envs));
        return request;
    }

    private static EnvVarDefinition envSimple(String name, String value) {
        return new EnvVarDefinition(name, new SimpleEnvVarValue(value), EnvVarMountType.CONTENT, name);
    }

    private static EnvVarDefinition envSensitive(String name, String value) {
        return new EnvVarDefinition(name, new SimpleEnvVarValue(value), EnvVarMountType.SECURE_CONTENT, name);
    }

    private static String sensitiveSecretName(Deployment deployment, String envName) {
        return deployment.getEnvs().stream()
                .filter(e -> e.getName().equals(envName))
                .filter(SensitiveEnvVar.class::isInstance)
                .map(SensitiveEnvVar.class::cast)
                .map(SensitiveEnvVar::getK8sSecretName)
                .findFirst()
                .orElse(null);
    }

    private Integer createRevisionFor(String resourceId) {
        return createRevisionByPredicate(a -> a.getActivityType() == ActivityType.Create
                && resourceId.equals(a.getResourceId()));
    }

    private Integer createRevisionForUuid(UUID resourceId) {
        return createRevisionByPredicate(a -> a.getActivityType() == ActivityType.Create
                && resourceId.toString().equals(a.getResourceId()));
    }

    private Integer createRevisionByPredicate(java.util.function.Predicate<AuditActivity> predicate) {
        PageRequestModel request = new PageRequestModel();
        request.setPageNumber(0);
        request.setPageSize(100);
        Sort sort = new Sort();
        sort.setColumn("epochTimestampMs");
        sort.setDirection(SortDirection.ASC);
        request.setSorts(List.of(sort));
        return auditActivityService.getActivitiesList(request).getData().stream()
                .filter(predicate)
                .findFirst()
                .map(AuditActivity::getRevision)
                .orElseThrow(() -> new IllegalStateException("Create activity not found"));
    }
}
