package com.epam.aidial.deployment.manager.service.deployment;

import com.epam.aidial.deployment.manager.cleanup.component.ComponentCleanupService;
import com.epam.aidial.deployment.manager.cleanup.resource.DisposableResourceManager;
import com.epam.aidial.deployment.manager.configuration.NodePoolProperties;
import com.epam.aidial.deployment.manager.configuration.logging.LogExecution;
import com.epam.aidial.deployment.manager.dao.entity.deployment.DeploymentEntity;
import com.epam.aidial.deployment.manager.dao.mapper.PersistenceDeploymentMapper;
import com.epam.aidial.deployment.manager.dao.repository.DeploymentRepository;
import com.epam.aidial.deployment.manager.exception.DeploymentException;
import com.epam.aidial.deployment.manager.exception.EntityNotFoundException;
import com.epam.aidial.deployment.manager.mapper.DeploymentMapper;
import com.epam.aidial.deployment.manager.model.ComponentRemoval;
import com.epam.aidial.deployment.manager.model.ComponentType;
import com.epam.aidial.deployment.manager.model.DeploymentMetadata;
import com.epam.aidial.deployment.manager.model.DeploymentStatus;
import com.epam.aidial.deployment.manager.model.EnvVar;
import com.epam.aidial.deployment.manager.model.EnvVarDefinition;
import com.epam.aidial.deployment.manager.model.EnvVarMountType;
import com.epam.aidial.deployment.manager.model.EnvVarValue;
import com.epam.aidial.deployment.manager.model.ImageDefinition;
import com.epam.aidial.deployment.manager.model.ImageStatus;
import com.epam.aidial.deployment.manager.model.ImageType;
import com.epam.aidial.deployment.manager.model.PodInfo;
import com.epam.aidial.deployment.manager.model.SensitiveEnvVar;
import com.epam.aidial.deployment.manager.model.SensitiveFileEnvVar;
import com.epam.aidial.deployment.manager.model.SimpleEnvVar;
import com.epam.aidial.deployment.manager.model.deployment.CreateDeployment;
import com.epam.aidial.deployment.manager.model.deployment.Deployment;
import com.epam.aidial.deployment.manager.model.deployment.InferenceDeployment;
import com.epam.aidial.deployment.manager.model.deployment.InternalImageSource;
import com.epam.aidial.deployment.manager.model.deployment.NimDeployment;
import com.epam.aidial.deployment.manager.model.deployment.Source;
import com.epam.aidial.deployment.manager.service.ImageDefinitionService;
import com.epam.aidial.deployment.manager.service.audit.HistoryService;
import com.epam.aidial.deployment.manager.service.nodepool.NodePoolService;
import com.epam.aidial.deployment.manager.service.security.SecurityClaimsExtractor;
import com.epam.aidial.deployment.manager.utils.EnvVarChangeDetector;
import com.epam.aidial.deployment.manager.web.dto.DeploymentTypeDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

@Slf4j
@Service
@LogExecution
@RequiredArgsConstructor
public class DeploymentService {

    private final DeploymentRepository deploymentRepository;
    private final ImageDefinitionService imageDefinitionService;
    private final ComponentCleanupService componentCleanupService;
    private final DeploymentMapper deploymentMapper;
    private final PersistenceDeploymentMapper persistenceDeploymentMapper;
    private final DeploymentManagerProvider deploymentManagerProvider;
    private final SecurityClaimsExtractor securityClaimsExtractor;
    private final DisposableResourceManager disposableResourceManager;
    private final HistoryService historyService;
    private final NodePoolProperties nodePoolProperties;
    private final NodePoolService nodePoolService;

    @Value("${app.deployment.reserved-env-names}")
    private final List<String> reservedEnvNames;

    @Transactional(readOnly = true)
    public Collection<Deployment> getAllDeployments() {
        return deploymentRepository.getAll();
    }

    @Transactional(readOnly = true)
    public Collection<Deployment> getAllDeployments(UUID imageDefinitionId) {
        return deploymentRepository.getAllByImageDefinitionId(imageDefinitionId);
    }

    @Transactional(readOnly = true)
    public Collection<Deployment> getAllDeploymentsByType(List<DeploymentTypeDto> types) {
        if (CollectionUtils.isEmpty(types)) {
            throw new IllegalArgumentException("Deployment types list cannot be empty or null");
        }

        if (types.size() == 1) {
            return deploymentRepository.getAllByType(types.getFirst());
        } else {
            return deploymentRepository.getAllByType(types);
        }
    }

    @Transactional(readOnly = true)
    public Optional<Deployment> getDeployment(String id) {
        return getDeployment(id, true);
    }

    @Transactional(readOnly = true)
    public Optional<Deployment> getDeployment(String id, boolean resolveSecrets) {
        var deployment = deploymentRepository.getById(id);
        if (resolveSecrets) {
            deployment.ifPresent(value -> deploymentManagerProvider.provide(id).resolveSecrets(value));
        }
        return deployment;
    }

    @Transactional(readOnly = true)
    public Deployment getDeploymentSnapshot(String id, Integer revision) {
        DeploymentEntity entity = historyService.entitySnapshotAtRevision(revision, id, DeploymentEntity.class);
        return persistenceDeploymentMapper.toDomain(entity);
    }

    @Transactional(readOnly = true)
    public Collection<Deployment> getAllDeploymentsAtRevision(Integer revision) {
        return historyService.getEntitiesAtRevision(revision, DeploymentEntity.class).stream()
                .map(persistenceDeploymentMapper::toDomain)
                .toList();
    }

    @Transactional
    public Deployment createDeployment(CreateDeployment request) {
        return createDeployment(request, true);
    }

    private Deployment createDeployment(CreateDeployment request, boolean applyNodePoolDefaultIfEmpty) {
        var id = request.getId();

        checkNoResourcesAreAssociatedWithId(id);
        DeploymentSourceValidator.validateSourceForDeploymentType(request);
        if (applyNodePoolDefaultIfEmpty) {
            applyCreateTimeNodePoolCascade(request);
        }
        validateNodePoolId(request.getNodePoolId());

        var envsPartition = validateAndPartitionEnvs(request.getMetadata());
        var deploymentManager = deploymentManagerProvider.provide(request);
        var envs = saveEnvVars(deploymentManager, id, envsPartition);
        var deployment = deploymentMapper.toDeployment(request, envs);

        resolveAndSetImageDefinitionRef(request, deployment);

        // Set author information - use provided author or extract from security context
        if (StringUtils.isBlank(request.getAuthor())) {
            deployment.setAuthor(securityClaimsExtractor.getEmail());
        } else {
            deployment.setAuthor(request.getAuthor());
        }

        var savedDeployment = deploymentRepository.save(deployment);
        savedDeployment.setEnvs(envs); // we do not save secret values into DB
        return savedDeployment;
    }

    @Transactional
    public Deployment rollback(String id, Integer revision) {
        // Reject ids past the highest assigned revision so that typos can't slip through to
        // reconcileEnvSecrets, which would silently wipe live sensitive env values. In-range
        // gap ids (e.g. left by Hibernate's pooled sequence allocator after a JVM restart)
        // are still resolved leniently by Envers downstream.
        if (revision == null || revision <= 0 || revision > historyService.maxRevisionId()) {
            throw new EntityNotFoundException("Unable to find revision with id " + revision);
        }

        var existingOpt = deploymentRepository.getById(id);
        if (existingOpt.isEmpty()) {
            // The deployment existed at the requested revision but has since been deleted: re-create it
            // instead of failing with 404, so an operator can revert a deletion in a single call.
            return resurrect(id, revision);
        }
        var existing = existingOpt.get();
        if (existing.getStatus().isActive()) {
            throw new IllegalArgumentException(
                    "Cannot roll back deployment '%s' while it is in active state '%s'. Undeploy it first."
                            .formatted(existing.getId(), existing.getStatus()));
        }

        var snapshot = getDeploymentSnapshot(id, revision);
        snapshot.setServiceName(existing.getServiceName());
        snapshot.setUrl(existing.getUrl());
        snapshot.setStatus(existing.getStatus());

        resolveImageDefinitionReference(snapshot);
        reconcileEnvSecrets(id, existing, snapshot);

        return deploymentRepository.update(id, snapshot);
    }

    /**
     * Re-create a deployment that existed at the requested revision but has since been deleted. The
     * snapshot is reconstructed from the audit history ({@link #getDeploymentSnapshot} throws 404 if the
     * id never existed at that revision). Any leftovers from the deleted generation are first drained so
     * the create path's {@code checkNoResourcesAreAssociatedWithId} guard passes and the scheduled
     * component cleaner cannot later re-delete the re-created deployment. The standard create path then
     * runs: the deployment comes back in NOT_DEPLOYED with no serviceName/url, and — since audit history
     * never stores sensitive env values — sensitive envs are provisioned empty and must be re-supplied
     * via {@code updateDeployment} before deploying, symmetric with {@link #reconcileEnvSecrets}.
     */
    private Deployment resurrect(String id, Integer revision) {
        var snapshot = getDeploymentSnapshot(id, revision);
        resolveImageDefinitionReference(snapshot);
        componentCleanupService.finalizePendingCleanup(id, ComponentType.DEPLOYMENT);

        var request = deploymentMapper.toCreateDeployment(snapshot);
        hydrateEnvValuesFromSnapshot(request, snapshot.getEnvs());
        return createDeployment(request, false);
    }

    private void hydrateEnvValuesFromSnapshot(CreateDeployment request, List<EnvVar> snapshotEnvs) {
        Map<String, EnvVarValue> valuesByName = new HashMap<>();
        for (EnvVar env : ListUtils.emptyIfNull(snapshotEnvs)) {
            valuesByName.put(env.getName(), env.getValue());
        }
        // Restore only non-sensitive (CONTENT) values from the snapshot; sensitive values are never
        // stored in audit history, so they stay null and must be re-supplied before deploy — mirroring
        // partitionFromSnapshot used by the update-based rollback path.
        ListUtils.emptyIfNull(request.getMetadata().getEnvs())
                .forEach(def -> def.setValue(def.getMountType() == EnvVarMountType.CONTENT
                        ? valuesByName.get(def.getName())
                        : null));
    }

    /**
     * Audit history never stores sensitive env values, so rollback cannot restore them. Always
     * cleanup the live K8s envs secret and reprovision one matching the snapshot's structure:
     * simple env values come from the snapshot, sensitive env values are reset to null. The
     * operator must re-supply sensitive values via {@code updateDeployment} before deploying.
     * This is intentionally symmetric with {@code updateDeployment} but without the change-detection
     * gate — silently retaining live sensitive values across a rollback would misrepresent the
     * snapshot the operator asked to restore.
     */
    private void reconcileEnvSecrets(String id, Deployment existing, Deployment snapshot) {
        var deploymentManager = deploymentManagerProvider.provide(id);
        // We deliberately skip resolveSecrets on `existing` here — cleanupSecrets keys off the
        // persisted k8sSecretName only, and that field is populated from the DB row, so the
        // resolved secret values are not needed (and asking for them would re-hit the K8s API
        // unnecessarily).
        deploymentManager.cleanupSecrets(id, existing.getEnvs());
        snapshot.setEnvs(saveEnvVars(deploymentManager, id, partitionFromSnapshot(snapshot.getEnvs())));
    }

    private EnvPartition partitionFromSnapshot(List<EnvVar> snapshotEnvs) {
        Map<String, EnvVarValue> sensitive = new HashMap<>();
        Map<String, EnvVarValue> nonSensitive = new HashMap<>();
        Map<String, EnvVarValue> sensitiveFile = new HashMap<>();

        for (EnvVar env : ListUtils.emptyIfNull(snapshotEnvs)) {
            if (env instanceof SensitiveFileEnvVar) {
                sensitiveFile.put(env.getName(), null);
            } else if (env instanceof SensitiveEnvVar) {
                sensitive.put(env.getName(), null);
            } else if (env instanceof SimpleEnvVar) {
                nonSensitive.put(env.getName(), env.getValue());
            }
        }
        return new EnvPartition(sensitive, nonSensitive, sensitiveFile);
    }

    @Transactional
    public Deployment updateDeployment(String id, CreateDeployment request) {
        if (!id.equals(request.getId())) {
            throw new IllegalArgumentException("Deployment ID in request path '%s' and in request body '%s' do not match"
                    .formatted(id, request.getId()));
        }
        DeploymentSourceValidator.validateSourceForDeploymentType(request);

        ImageDefinition imageDefinition = resolveImageDefinition(request).orElse(null);

        var envsPartition = validateAndPartitionEnvs(request.getMetadata());
        var existingDeployment = deploymentRepository.getById(id).orElseThrow(notFound("Deployment", id));

        validateNodePoolId(request.getNodePoolId());
        var existingStatus = existingDeployment.getStatus();

        if (existingStatus.isIntermediate()) {
            throw new IllegalStateException("Deployment '%s' has an intermediate status '%s'. Update is not allowed"
                    .formatted(id, existingStatus));
        }

        var deploymentManager = deploymentManagerProvider.provide(id);
        var existingDeploymentWithResolvedSecrets = deploymentManager.resolveSecrets(existingDeployment);

        List<EnvVar> envs = existingDeploymentWithResolvedSecrets.getEnvs();

        boolean envsAreChanged = EnvVarChangeDetector.areEnvsChanged(envs, request.getMetadata());
        if (envsAreChanged) {
            deploymentManager.cleanupSecrets(id, existingDeployment.getEnvs());
            envs = saveEnvVars(deploymentManager, id, envsPartition);
        }

        var deployment = deploymentMapper.toDeployment(request, envs);
        deployment.setUrl(existingDeployment.getUrl());
        deployment.setServiceName(existingDeployment.getServiceName());

        var status = existingStatus == DeploymentStatus.STOPPED ? DeploymentStatus.NOT_DEPLOYED : existingStatus;
        deployment.setStatus(status);

        if (imageDefinition != null) {
            setDeploymentImageDefinitionRef(deployment, imageDefinition);
        }

        var updatedDeployment = deploymentRepository.update(id, deployment);

        boolean isApplicableForCiliumNetworkPolicyUpdate = isApplicableForCiliumNetworkPolicyUpdate(existingDeploymentWithResolvedSecrets, updatedDeployment);
        boolean isApplicableForRollingUpdate = isApplicableForRollingUpdate(existingDeploymentWithResolvedSecrets, updatedDeployment, envsAreChanged);

        // Skip when rollingUpdate also fires — its afterCommit already refreshes the CNP using
        // the freshly-built spec (no extra cluster read).
        if (updatedDeployment.getStatus() == DeploymentStatus.RUNNING
                && isApplicableForCiliumNetworkPolicyUpdate
                && !isApplicableForRollingUpdate) {
            deploymentManager.updateCiliumNetworkPolicy(id);
        }

        if (updatedDeployment.getStatus() == DeploymentStatus.RUNNING && isApplicableForRollingUpdate) {
            updatedDeployment = deploymentManager.rollingUpdate(id);
        } else if (updatedDeployment.getStatus() == DeploymentStatus.CRASHED) {
            updatedDeployment = deploymentManager.undeploy(id);
        }

        updatedDeployment.setEnvs(envs); // we do not save secret values into DB
        return updatedDeployment;
    }

    @Transactional
    public void deleteDeployment(String id) {
        undeploy(id);
        componentCleanupService.deleteAsync(ComponentRemoval.of(id, ComponentType.DEPLOYMENT));
    }

    public Deployment deploy(String id) {
        var deployment = deploymentRepository.getById(id).orElseThrow(notFound("Deployment", id));
        requireImageBuiltForDeployment(deployment);
        var deploymentManager = deploymentManagerProvider.provide(id);
        var deployed = deploymentManager.deploy(id);
        return deploymentManager.resolveSecrets(deployed);
    }

    public Deployment undeploy(String id) {
        var deploymentManager = deploymentManagerProvider.provide(id);
        var deployment = deploymentManager.undeploy(id);
        return deploymentManager.resolveSecrets(deployment);
    }

    @Transactional
    public Deployment duplicateDeployment(String etalonDeploymentId, String cloneDeploymentId, String cloneDeploymentDisplayName) {
        // Get the etalon deployment with secrets resolved
        var etalonDeployment = getDeployment(etalonDeploymentId, true)
                .orElseThrow(() -> new EntityNotFoundException("Etalon deployment not found by id: '%s'".formatted(etalonDeploymentId)));

        var createDeployment = deploymentMapper.toCreateCloneDeployment(etalonDeployment, cloneDeploymentId, cloneDeploymentDisplayName);
        createDeployment.setAuthor(securityClaimsExtractor.getEmail());

        return createDeployment(createDeployment, false);
    }

    @Transactional
    public void updateImageDefinitionForDeployments(UUID imageDefinitionId, List<String> deployments) {
        var imageDefinition = loadImageDefinitionOrThrow(imageDefinitionId);
        var imageType = ImageType.of(imageDefinition);

        var loaded = deploymentRepository.getByIds(deployments);
        if (loaded.size() != deployments.size()) {
            var foundIds = loaded.stream().map(Deployment::getId).collect(Collectors.toSet());
            var missing = deployments.stream().filter(id -> !foundIds.contains(id)).toList();
            throw notFound("Deployment(s)", missing).get();
        }
        loaded.forEach(deployment -> DeploymentSourceValidator.validateImageTypeMatchesDeployment(deployment, imageType));

        deploymentRepository.updateImageDefinitionForDeployments(imageDefinition, imageType, deployments);
        deployments.forEach(this::rollingUpdate);
    }

    private void rollingUpdate(String id) {
        var deployment = deploymentRepository.getById(id).orElseThrow(notFound("Deployment", id));
        var deploymentManager = deploymentManagerProvider.provide(id);
        if (deployment.getStatus() == DeploymentStatus.RUNNING) {
            try {
                deploymentManager.rollingUpdate(id);
            } catch (Exception e) {
                log.warn("Failed to perform rolling update. Deployment '{}'", id, e);
            }
        } else if (deployment.getStatus() == DeploymentStatus.CRASHED) {
            try {
                deploymentManager.undeploy(id);
            } catch (Exception e) {
                log.warn("Failed to stop deployment. Deployment '{}'", id, e);
            }
        }
    }

    public List<PodInfo> getActiveInstances(String id) {
        return deploymentManagerProvider.provide(id)
                .getActiveInstances(id);
    }

    public List<PodInfo> getInstances(String id) {
        return deploymentManagerProvider.provide(id)
                .getInstances(id);
    }

    private void checkNoResourcesAreAssociatedWithId(String id) {
        var existingDisposableResources = disposableResourceManager.getAllByGroupId(id);
        if (CollectionUtils.isNotEmpty(existingDisposableResources)) {
            throw new IllegalArgumentException("Failed to create deployment with ID '%s'. There are resources awaiting deletion associated with this ID."
                    .formatted(id));
        }
    }

    private EnvPartition validateAndPartitionEnvs(DeploymentMetadata metadata) {
        var envDefs = ListUtils.emptyIfNull(metadata.getEnvs()).stream()
                .collect(Collectors.toMap(EnvVarDefinition::getName, Function.identity()));

        envDefs.keySet().forEach(this::validateEnvNameNotReserved);

        Map<String, EnvVarValue> sens = new HashMap<>();
        Map<String, EnvVarValue> nonSens = new HashMap<>();
        Map<String, EnvVarValue> sensFile = new HashMap<>();

        envDefs.forEach((key, envDef) -> {
            Map<String, EnvVarValue> typedEnvCollection;
            var mountType = envDef.getMountType();
            switch (mountType) {
                case CONTENT -> typedEnvCollection = nonSens;
                case SECURE_CONTENT -> typedEnvCollection = sens;
                case SECURE_FILE -> typedEnvCollection = sensFile;
                default ->
                        throw new IllegalStateException("Unexpected mount type '%s' for `%s' env variable".formatted(mountType, key));
            }
            typedEnvCollection.put(key, envDef.getValue());
        });

        // Validate base64 encoding for sensitive file content
        sensFile.forEach(DeploymentService::validateBase64Encoding);

        // Return the original sensFile content for secret creation (not the _FILE suffix versions)
        return new EnvPartition(sens, nonSens, sensFile);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private List<EnvVar> saveEnvVars(DeploymentManager deploymentManager, String deploymentId, EnvPartition partition) {
        var sensitive = deploymentManager.provisionSecrets(deploymentId, partition);
        var simple = toSimpleEnvs(partition.nonSensitive());
        return ListUtils.union(sensitive, simple);
    }

    private Optional<ImageDefinition> resolveImageDefinition(CreateDeployment request) {
        Optional<ImageDefinition> definition = lookupImageDefinitionBySource(
                request.getSource(),
                id -> new EntityNotFoundException("ImageDefinition not found: '%s'".formatted(id)),
                src -> new EntityNotFoundException("ImageDefinition not found. Type='%s'. Name='%s'. Version='%s'"
                        .formatted(src.imageDefinitionType(), src.imageDefinitionName(), src.imageDefinitionVersion())));
        definition.ifPresent(d -> DeploymentSourceValidator.validateImageTypeMatchesDeployment(request, ImageType.of(d)));
        return definition;
    }

    private void resolveImageDefinitionReference(Deployment deployment) {
        lookupImageDefinitionBySource(
                deployment.getSource(),
                id -> new IllegalArgumentException(
                        "Image definition with id '%s' not found (referenced by deployment '%s')"
                                .formatted(id, deployment.getId())),
                src -> new IllegalArgumentException(
                        "Image definition with type='%s', name='%s', version='%s' not found (referenced by deployment '%s')"
                                .formatted(src.imageDefinitionType(), src.imageDefinitionName(), src.imageDefinitionVersion(), deployment.getId())))
                .ifPresent(referenced -> setDeploymentImageDefinitionRef(deployment, referenced));
    }

    /**
     * Resolve the {@link ImageDefinition} referenced by an {@link InternalImageSource}. Callers
     * supply exception factories so the create/update path (EntityNotFoundException) and the
     * rollback path (IllegalArgumentException with deployment-id context) can each meet their
     * respective contract messages.
     */
    private Optional<ImageDefinition> lookupImageDefinitionBySource(
            Source source,
            Function<UUID, RuntimeException> notFoundById,
            Function<InternalImageSource, RuntimeException> notFoundByTriple) {
        if (!(source instanceof InternalImageSource src)) {
            return Optional.empty();
        }
        if (src.imageDefinitionId() != null) {
            return Optional.of(imageDefinitionService.getImageDefinition(src.imageDefinitionId())
                    .orElseThrow(() -> notFoundById.apply(src.imageDefinitionId())));
        }
        if (src.imageDefinitionType() != null
                && StringUtils.isNotBlank(src.imageDefinitionName())
                && StringUtils.isNotBlank(src.imageDefinitionVersion())) {
            return Optional.of(imageDefinitionService.getImageDefinitionByTypeAndNameAndVersion(
                    src.imageDefinitionType(), src.imageDefinitionName(), src.imageDefinitionVersion())
                    .orElseThrow(() -> notFoundByTriple.apply(src)));
        }
        return Optional.empty();
    }

    private void resolveAndSetImageDefinitionRef(CreateDeployment request, Deployment deployment) {
        resolveImageDefinition(request).ifPresent(imageDefinition -> setDeploymentImageDefinitionRef(deployment, imageDefinition));
    }

    private void setDeploymentImageDefinitionRef(Deployment deployment, ImageDefinition imageDefinition) {
        deployment.setSource(new InternalImageSource(
                imageDefinition.getId(),
                ImageType.of(imageDefinition),
                imageDefinition.getName(),
                imageDefinition.getVersion()
        ));
    }

    private ImageDefinition loadImageDefinitionOrThrow(UUID id) {
        var imageDefinition = imageDefinitionService.getImageDefinition(id)
                .orElseThrow(notFound("ImageDefinition", id));
        if (imageDefinition.getBuildStatus() != ImageStatus.BUILD_SUCCESSFUL) {
            throw new DeploymentException("Unable to perform requested operation: image '%s' is not built".formatted(id));
        }
        return imageDefinition;
    }

    private void requireImageBuiltForDeployment(Deployment deployment) {
        if (deployment.getSource() instanceof InternalImageSource internalSource) {
            loadImageDefinitionOrThrow(internalSource.imageDefinitionId());
        }
    }

    private static List<SimpleEnvVar> toSimpleEnvs(Map<String, EnvVarValue> envs) {
        return envs.entrySet().stream()
                .map(e -> new SimpleEnvVar(e.getKey(), e.getValue()))
                .toList();
    }

    private static <T> Supplier<EntityNotFoundException> notFound(String what, T id) {
        return () -> new EntityNotFoundException("%s not found: '%s'".formatted(what, id));
    }

    private void validateNodePoolId(String nodePoolId) {
        if (StringUtils.isNotBlank(nodePoolId) && nodePoolProperties.findById(nodePoolId).isEmpty()) {
            throw new IllegalArgumentException("Node pool id '%s' is not configured".formatted(nodePoolId));
        }
    }

    /**
     * FR-018: when the create payload leaves {@code nodePoolId} null, resolve via the cascade and
     * stamp the result onto the request so it persists onto the record. The duplicate flow opts
     * out of this entirely by calling the worker with {@code applyNodePoolDefaultIfEmpty=false}.
     */
    private void applyCreateTimeNodePoolCascade(CreateDeployment request) {
        if (request.getNodePoolId() != null) {
            return;
        }
        request.setNodePoolId(nodePoolService.resolveForCreate(request));
    }

    private void validateEnvNameNotReserved(String name) {
        if (reservedEnvNames.contains(name)) {
            throw new IllegalArgumentException("Environment variable name '%s' is reserved and cannot be used"
                    .formatted(name));
        }
    }

    private static void validateBase64Encoding(String name, EnvVarValue envVarValue) {
        if (envVarValue == null || envVarValue.getValue() == null) {
            return;
        }
        if (!isBase64Encoded(envVarValue.getValue())) {
            throw new IllegalArgumentException("Env variable '%s' should contain base64-encoded file content. Actual content: %s"
                    .formatted(name, envVarValue.getValue()));
        }
    }

    private static boolean isBase64Encoded(String value) {
        try {
            byte[] decodedBytes = Base64.decodeBase64(value);
            String reEncoded = Base64.encodeBase64String(decodedBytes);
            return value.equals(reEncoded);
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean isApplicableForRollingUpdate(Deployment existing, Deployment updated, boolean envsAreChanged) {
        // 1. Check specialized deployment types using pattern matching
        boolean specializedUpdate = switch (existing) {
            case NimDeployment exNim when updated instanceof NimDeployment upNim ->
                    !Objects.equals(exNim.getContainerGrpcPort(), upNim.getContainerGrpcPort());

            case InferenceDeployment exInf when updated instanceof InferenceDeployment upInf ->
                    !Objects.equals(exInf.getArgs(), upInf.getArgs())
                            || !Objects.equals(exInf.getCommand(), upInf.getCommand());

            default -> false;
        };

        // 2. Check general deployment fields (and env changes)
        return envsAreChanged
                || specializedUpdate
                || !Objects.equals(existing.getSource(), updated.getSource())
                || !Objects.equals(existing.getContainerPort(), updated.getContainerPort())
                || !Objects.equals(existing.getScaling(), updated.getScaling())
                || !Objects.equals(existing.getResources(), updated.getResources())
                || !Objects.equals(existing.getNodePoolId(), updated.getNodePoolId());
    }

    private static boolean isApplicableForCiliumNetworkPolicyUpdate(Deployment existing, Deployment updated) {
        return !CollectionUtils.isEqualCollection(existing.getAllowedDomains(), updated.getAllowedDomains())
                || !Objects.equals(existing.getContainerPort(), updated.getContainerPort());
    }

}
