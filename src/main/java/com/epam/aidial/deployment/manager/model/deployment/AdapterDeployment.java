package com.epam.aidial.deployment.manager.model.deployment;

import com.epam.aidial.deployment.manager.model.Scaling;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.jetbrains.annotations.Nullable;

@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class AdapterDeployment extends Deployment {
    @Nullable
    private Scaling scaling;
}
