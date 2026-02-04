package com.epam.aidial.deployment.manager.web.dto.probe;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class HttpGetProbeDto implements ProbeHandlerDto {
    /**
     * Path to access on the HTTP server.
     */
    @NotNull
    @Pattern(regexp = "^/[a-zA-Z0-9/_-]*$", message = "Must be a valid URL path starting with /")
    private String path;
    /**
     * Number of the port to access on the container.
     */
    @NotNull
    @Min(1) @Max(65535)
    private Integer port;
}
