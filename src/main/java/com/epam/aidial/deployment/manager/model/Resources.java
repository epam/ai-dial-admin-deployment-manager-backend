package com.epam.aidial.deployment.manager.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Collections;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Resources {
    private Map<String, String> limits = Collections.emptyMap();
    private Map<String, String> requests = Collections.emptyMap();
}
