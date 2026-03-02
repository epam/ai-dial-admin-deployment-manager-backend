package com.epam.aidial.deployment.manager.model.deployment;

import com.epam.aidial.deployment.manager.model.McpTransport;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
public class McpDeployment extends Deployment {
    private String imageReference;
    private McpTransport transport;
    private String mcpEndpointPath;
}
