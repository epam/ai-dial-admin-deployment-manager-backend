package com.epam.aidial.deployment.manager.service.deployment;

import com.epam.aidial.deployment.manager.cleanup.component.ComponentCleanupService;
import com.epam.aidial.deployment.manager.cleanup.resource.DisposableResourceManager;
import com.epam.aidial.deployment.manager.configuration.logging.LogExecution;
import com.epam.aidial.deployment.manager.dao.repository.DeploymentRepository;
import com.epam.aidial.deployment.manager.exception.DeploymentException;
import com.epam.aidial.deployment.manager.exception.EntityNotFoundException;
import com.epam.aidial.deployment.manager.mapper.DeploymentMapper;
import com.epam.aidial.deployment.manager.model.AdapterImageDefinition;
import com.epam.aidial.deployment.manager.model.ComponentRemoval;
import com.epam.aidial.deployment.manager.model.ComponentType;
import com.epam.aidial.deployment.manager.model.DeploymentMetadata;
import com.epam.aidial.deployment.manager.model.DeploymentStatus;
import com.epam.aidial.deployment.manager.model.EnvVar;
import com.epam.aidial.deployment.manager.model.EnvVarDefinition;
import com.epam.aidial.deployment.manager.model.EnvVarValue;
import com.epam.aidial.deployment.manager.model.ImageDefinition;
import com.epam.aidial.deployment.manager.model.ImageStatus;
import com.epam.aidial.deployment.manager.model.ImageType;
import com.epam.aidial.deployment.manager.model.InterceptorImageDefinition;
import com.epam.aidial.deployment.manager.model.McpImageDefinition;
import com.epam.aidial.deployment.manager.model.PodInfo;
import com.epam.aidial.deployment.manager.model.SimpleEnvVar;
import com.epam.aidial.deployment.manager.model.deployment.CreateDeployment;
import com.epam.aidial.deployment.manager.model.deployment.CreateInferenceDeployment;
import com.epam.aidial.deployment.manager.model.deployment.CreateNimDeployment;
import com.epam.aidial.deployment.manager.model.deployment.Deployment;
import com.epam.aidial.deployment.manager.model.deployment.HuggingFaceSource;
import com.epam.aidial.deployment.manager.model.deployment.ImageReferenceSource;
import com.epam.aidial.deployment.manager.model.deployment.InferenceDeployment;
import com.epam.aidial.deployment.manager.model.deployment.InternalImageSource;
import com.epam.aidial.deployment.manager.model.deployment.NgcRegistrySource;
import com.epam.aidial.deployment.manager.model.deployment.NimDeployment;
import com.epam.aidial.deployment.manager.model.deployment.Source;
import com.epam.aidial.deployment.manager.service.ImageDefinitionService;
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
    private final DeploymentManagerProvider deploymentManagerProvider;
    private final SecurityClaimsExtractor securityClaimsExtractor;
    private final DisposableResourceManager disposableResourceManager;

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

    @Transactional
    public Deployment createDeployment(CreateDeployment request) {
        var id = request.getId();
        validateSourceForDeploymentType(request);

        checkNoResourcesAreAssociatedWithId(id);

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
    public Deployment updateDeployment(String id, CreateDeployment request) {
        if (!id.equals(request.getId())) {
            throw new IllegalArgumentException("Deployment ID in request path '%s' and in request body '%s' do not match"
                    .formatted(id, request.getId()));
        }
        validateSourceForDeploymentType(request);

        ImageDefinition imageDefinition = resolveImageDefinition(request).orElse(null);

        var envsPartition = validateAndPartitionEnvs(request.getMetadata());
        var existingDeployment = deploymentRepository.getById(id).orElseThrow(notFound("Deployment", id));
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

        var status = existingStatus == DeploymentStatus.STOPPED ? DeploymentStatus.NOT_DEPLOYED : existingStatus;
        deployment.setStatus(status);

        if (imageDefinition != null) {
            setDeploymentImageDefinitionRef(deployment, imageDefinition);
        }

        var updatedDeployment = deploymentRepository.update(id, deployment);

        boolean isApplicableForCiliumNetworkPolicyUpdate = isApplicableForCiliumNetworkPolicyUpdate(existingDeploymentWithResolvedSecrets, updatedDeployment);
        if (updatedDeployment.getStatus() == DeploymentStatus.RUNNING && isApplicableForCiliumNetworkPolicyUpdate) {
            deploymentManager.updateCiliumNetworkPolicy(id);
        }

        boolean isApplicableForRollingUpdate = isApplicableForRollingUpdate(existingDeploymentWithResolvedSecrets, updatedDeployment, envsAreChanged);
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

        return createDeployment(createDeployment);
    }

    @Transactional
    public void updateImageDefinitionForDeployments(UUID imageDefinitionId, List<String> deployments) {
        var imageDefinition = loadImageDefinitionOrThrow(imageDefinitionId);
        var imageType = getImageDefinitionType(imageDefinition);
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
        sensFile.forEach((key, value) -> validateBase64Encoding(key, value.getValue()));

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
        if (!(request.getSource() instanceof InternalImageSource internalSource)) {
            return Optional.empty();
        }

        var id = internalSource.imageDefinitionId();
        var name = internalSource.imageDefinitionName();
        var version = internalSource.imageDefinitionVersion();
        var type = internalSource.imageDefinitionType();

        if (id != null) {
            var definition = imageDefinitionService.getImageDefinition(id)
                    .orElseThrow(notFound("ImageDefinition", id));
            return Optional.ofNullable(definition);
        }

        if (type != null
                && StringUtils.isNotBlank(name)
                && StringUtils.isNotBlank(version)) {
            var definition = imageDefinitionService.getImageDefinitionByTypeAndNameAndVersion(type, name, version)
                    .orElseThrow(
                        () -> new EntityNotFoundException("ImageDefinition not found. Type='%s'. Name='%s'. Version='%s'".formatted(type, name, version)));
            return Optional.ofNullable(definition);
        }

        return Optional.empty();
    }

    private void resolveAndSetImageDefinitionRef(CreateDeployment request, Deployment deployment) {
        resolveImageDefinition(request).ifPresent(imageDefinition -> setDeploymentImageDefinitionRef(deployment, imageDefinition));
    }

    private void setDeploymentImageDefinitionRef(Deployment deployment, ImageDefinition imageDefinition) {
        deployment.setSource(new InternalImageSource(
                imageDefinition.getId(),
                getImageDefinitionType(imageDefinition),
                imageDefinition.getName(),
                imageDefinition.getVersion()
        ));
    }

    private static ImageType getImageDefinitionType(ImageDefinition imageDefinition) {
        return switch (imageDefinition) {
            case McpImageDefinition ignored -> ImageType.MCP;
            case AdapterImageDefinition ignored -> ImageType.ADAPTER;
            case InterceptorImageDefinition ignored -> ImageType.INTERCEPTOR;
            default -> throw new IllegalArgumentException("Unknown image definition type: " + imageDefinition.getClass().getName());
        };
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

    private void validateEnvNameNotReserved(String name) {
        if (reservedEnvNames.contains(name)) {
            throw new IllegalArgumentException("Environment variable name '%s' is reserved and cannot be used"
                    .formatted(name));
        }
    }

    private static void validateBase64Encoding(String name, String value) {
        if (value == null) {
            return;
        }
        if (!isBase64Encoded(value)) {
            throw new IllegalArgumentException("Env variable '%s' should contain base64-encoded file content. Actual content: %s"
                    .formatted(name, value));
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

    private static void validateSourceForDeploymentType(CreateDeployment request) {
        Source source = request.getSource();
        if (source == null) {
            throw new IllegalArgumentException("Deployment '%s' source must not be null".formatted(request.getId()));
        }

        boolean valid = switch (request) {
            case CreateNimDeployment ignored -> source instanceof NgcRegistrySource;
            case CreateInferenceDeployment ignored -> source instanceof HuggingFaceSource;
            default -> source instanceof InternalImageSource || source instanceof ImageReferenceSource;
        };

        if (!valid) {
            throw new IllegalArgumentException("Invalid source type '%s' for deployment '%s' of type '%s'"
                    .formatted(source.getClass().getSimpleName(), request.getId(), request.getClass().getSimpleName()));
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
                || !Objects.equals(existing.getResources(), updated.getResources());
    }

    private static boolean isApplicableForCiliumNetworkPolicyUpdate(Deployment existing, Deployment updated) {
        return !CollectionUtils.isEqualCollection(existing.getAllowedDomains(), updated.getAllowedDomains())
                || !Objects.equals(existing.getContainerPort(), updated.getContainerPort());
    }

}
