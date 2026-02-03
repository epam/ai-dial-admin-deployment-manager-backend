package com.epam.aidial.deployment.manager.web.dto.probe;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ExecProbeDto implements ProbeHandlerDto {
    /**
     * Command and arguments to execute. Cannot be empty.
     */
    private List<String> command;
}
