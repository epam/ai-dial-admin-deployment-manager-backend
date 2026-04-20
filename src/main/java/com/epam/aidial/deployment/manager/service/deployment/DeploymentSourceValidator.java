package com.epam.aidial.deployment.manager.service.deployment;

import com.epam.aidial.deployment.manager.model.ImageType;
import com.epam.aidial.deployment.manager.model.deployment.AdapterDeployment;
import com.epam.aidial.deployment.manager.model.deployment.ApplicationDeployment;
import com.epam.aidial.deployment.manager.model.deployment.CreateAdapterDeployment;
import com.epam.aidial.deployment.manager.model.deployment.CreateApplicationDeployment;
import com.epam.aidial.deployment.manager.model.deployment.CreateDeployment;
import com.epam.aidial.deployment.manager.model.deployment.CreateInferenceDeployment;
import com.epam.aidial.deployment.manager.model.deployment.CreateInterceptorDeployment;
import com.epam.aidial.deployment.manager.model.deployment.CreateMcpDeployment;
import com.epam.aidial.deployment.manager.model.deployment.CreateNimDeployment;
import com.epam.aidial.deployment.manager.model.deployment.Deployment;
import com.epam.aidial.deployment.manager.model.deployment.HuggingFaceSource;
import com.epam.aidial.deployment.manager.model.deployment.ImageReferenceSource;
import com.epam.aidial.deployment.manager.model.deployment.InterceptorDeployment;
import com.epam.aidial.deployment.manager.model.deployment.InternalImageSource;
import com.epam.aidial.deployment.manager.model.deployment.McpDeployment;
import com.epam.aidial.deployment.manager.model.deployment.NgcRegistrySource;
import com.epam.aidial.deployment.manager.model.deployment.Source;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.util.Optional;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class DeploymentSourceValidator {

    public static void validateSourceForDeploymentType(CreateDeployment request) {
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

        if (source instanceof InternalImageSource internal && internal.imageDefinitionType() != null) {
            validateImageTypeMatchesDeployment(request, internal.imageDefinitionType());
        }
    }

    public static void validateImageTypeMatchesDeployment(CreateDeployment request, ImageType actual) {
        expectedImageTypeFor(request).ifPresent(expected -> {
            if (expected != actual) {
                throw new IllegalArgumentException(
                        "Deployment '%s' of type '%s' cannot use image of type '%s'; expected '%s'"
                                .formatted(request.getId(), request.getClass().getSimpleName(), actual, expected));
            }
        });
    }

    public static void validateImageTypeMatchesDeployment(Deployment deployment, ImageType actual) {
        expectedImageTypeFor(deployment).ifPresent(expected -> {
            if (expected != actual) {
                throw new IllegalArgumentException(
                        "Deployment '%s' of type '%s' cannot use image of type '%s'; expected '%s'"
                                .formatted(deployment.getId(), deployment.getClass().getSimpleName(), actual, expected));
            }
        });
    }

    private static Optional<ImageType> expectedImageTypeFor(CreateDeployment request) {
        return switch (request) {
            case CreateMcpDeployment ignored -> Optional.of(ImageType.MCP);
            case CreateAdapterDeployment ignored -> Optional.of(ImageType.ADAPTER);
            case CreateInterceptorDeployment ignored -> Optional.of(ImageType.INTERCEPTOR);
            case CreateApplicationDeployment ignored -> Optional.of(ImageType.APPLICATION);
            default -> Optional.empty();
        };
    }

    private static Optional<ImageType> expectedImageTypeFor(Deployment deployment) {
        return switch (deployment) {
            case McpDeployment ignored -> Optional.of(ImageType.MCP);
            case AdapterDeployment ignored -> Optional.of(ImageType.ADAPTER);
            case InterceptorDeployment ignored -> Optional.of(ImageType.INTERCEPTOR);
            case ApplicationDeployment ignored -> Optional.of(ImageType.APPLICATION);
            default -> Optional.empty();
        };
    }
}
