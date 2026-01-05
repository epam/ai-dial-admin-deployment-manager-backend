package com.epam.aidial.deployment.manager.web.dto.deployment;

import com.epam.aidial.deployment.manager.web.dto.McpTransportDto;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.jetbrains.annotations.Nullable;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
public class McpDeploymentDto extends ImageBasedDeploymentDto {
    @Nullable
    private McpTransportDto transport;
    @Nullable
    @Pattern(regexp = "^/[a-zA-Z0-9/_-]*$", message = "Must be a valid URL path starting with /")
    private String mcpEndpointPath;
}
