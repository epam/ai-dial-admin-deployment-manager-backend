package com.epam.aidial.deployment.manager.model.probe;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ExecProbe implements ProbeHandler {
    private List<String> command;
}
