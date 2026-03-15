package com.epam.aidial.deployment.manager.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;
import org.jetbrains.annotations.Nullable;

import java.util.List;

@Data
@SuperBuilder
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
public class GitDockerfileImageSource extends ImageSource {
    private String url;
    private String branchName;
    private String sha;
    private String baseDirectory;
    private List<String> entrypoint;
    @Nullable
    private ExternalRegistryRef externalRegistryRef;
}
