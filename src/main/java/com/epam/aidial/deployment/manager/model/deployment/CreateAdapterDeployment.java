package com.epam.aidial.deployment.manager.model.deployment;

import com.epam.aidial.deployment.manager.model.Scaling;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.jetbrains.annotations.Nullable;

@Getter
@Setter
@SuperBuilder
public class CreateAdapterDeployment extends CreateDeployment {
    @Nullable
    private Scaling scaling;
}
