package com.epam.aidial.deployment.manager.utils;

import com.epam.aidial.deployment.manager.model.DeploymentMetadata;
import com.epam.aidial.deployment.manager.model.EnvVar;
import com.epam.aidial.deployment.manager.model.EnvVarDefinition;
import com.epam.aidial.deployment.manager.model.EnvVarMountType;
import com.epam.aidial.deployment.manager.model.FileEnvVarValue;
import com.epam.aidial.deployment.manager.model.SensitiveEnvVar;
import com.epam.aidial.deployment.manager.model.SensitiveFileEnvVar;
import com.epam.aidial.deployment.manager.model.SimpleEnvVar;
import com.epam.aidial.deployment.manager.model.SimpleEnvVarValue;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class EnvVarChangeDetectorTest {

    @Test
    void testNullMetadata() {
        List<EnvVar> list = Collections.singletonList(createSimpleEnvVar("VAR1", "value1"));
        assertThat(EnvVarChangeDetector.areEnvsChanged(list, null)).isTrue();
        assertThat(EnvVarChangeDetector.areEnvsChanged(null, null)).isFalse();
        assertThat(EnvVarChangeDetector.areEnvsChanged(new ArrayList<>(), null)).isFalse();
    }

    @Test
    void testNullEnvsList() {
        DeploymentMetadata metadata = new DeploymentMetadata();
        metadata.setEnvs(Collections.singletonList(createEnvVarDefinition("VAR1", "value1", EnvVarMountType.CONTENT)));
        assertThat(EnvVarChangeDetector.areEnvsChanged(null, metadata)).isTrue();
        assertThat(EnvVarChangeDetector.areEnvsChanged(new ArrayList<>(), new DeploymentMetadata(null))).isFalse();
        assertThat(EnvVarChangeDetector.areEnvsChanged(null, new DeploymentMetadata(new ArrayList<>()))).isFalse();
    }

    @Test
    void testDifferentSizesWithMetadata() {
        List<EnvVar> list = Collections.singletonList(createSimpleEnvVar("VAR1", "value1"));

        DeploymentMetadata metadata = new DeploymentMetadata();
        metadata.setEnvs(Arrays.asList(
                createEnvVarDefinition("VAR1", "value1", EnvVarMountType.CONTENT),
                createEnvVarDefinition("VAR2", "value2", EnvVarMountType.CONTENT)
        ));

        assertThat(EnvVarChangeDetector.areEnvsChanged(list, metadata)).isTrue();
    }

    @Test
    void testDifferentKeysWithMetadata() {
        List<EnvVar> list = Collections.singletonList(createSimpleEnvVar("VAR1", "value1"));

        DeploymentMetadata metadata = new DeploymentMetadata();
        metadata.setEnvs(Collections.singletonList(
                createEnvVarDefinition("VAR2", "value1", EnvVarMountType.CONTENT)
        ));

        assertThat(EnvVarChangeDetector.areEnvsChanged(list, metadata)).isTrue();
    }

    @Test
    void testSameKeysButDifferentValuesWithMetadata() {
        List<EnvVar> list = Collections.singletonList(createSimpleEnvVar("VAR1", "value1"));

        DeploymentMetadata metadata = new DeploymentMetadata();
        metadata.setEnvs(Collections.singletonList(
                createEnvVarDefinition("VAR1", "value2", EnvVarMountType.CONTENT)
        ));

        assertThat(EnvVarChangeDetector.areEnvsChanged(list, metadata)).isTrue();
    }

    @Test
    void testSameKeysAndValuesWithMetadata() {
        List<EnvVar> list = Arrays.asList(
                createSimpleEnvVar("VAR1", "value1"),
                createSimpleEnvVar("VAR2", "value2")
        );

        DeploymentMetadata metadata = new DeploymentMetadata();
        metadata.setEnvs(Arrays.asList(
                createEnvVarDefinition("VAR1", "value1", EnvVarMountType.CONTENT),
                createEnvVarDefinition("VAR2", "value2", EnvVarMountType.CONTENT)
        ));

        assertThat(EnvVarChangeDetector.areEnvsChanged(list, metadata)).isFalse();
    }

    @Test
    void testDifferentOrderSameContentWithMetadata() {
        List<EnvVar> list = Arrays.asList(
                createSimpleEnvVar("VAR1", "value1"),
                createSimpleEnvVar("VAR2", "value2")
        );

        DeploymentMetadata metadata = new DeploymentMetadata();
        metadata.setEnvs(Arrays.asList(
                createEnvVarDefinition("VAR2", "value2", EnvVarMountType.CONTENT),
                createEnvVarDefinition("VAR1", "value1", EnvVarMountType.CONTENT)
        ));

        assertThat(EnvVarChangeDetector.areEnvsChanged(list, metadata)).isFalse();
    }

    @Test
    void testFileEnvVarValueWithMetadata() {
        List<EnvVar> list = Collections.singletonList(createFileEnvVar());

        DeploymentMetadata metadata = new DeploymentMetadata();
        metadata.setEnvs(Collections.singletonList(
                createFileEnvVarDefinition("file.txt")
        ));

        assertThat(EnvVarChangeDetector.areEnvsChanged(list, metadata)).isFalse();

        // Different file name
        metadata.setEnvs(Collections.singletonList(
                createFileEnvVarDefinition("different.txt")
        ));

        assertThat(EnvVarChangeDetector.areEnvsChanged(list, metadata)).isTrue();
    }

    @Test
    void testMixedEnvVarTypesWithMetadata() {
        List<EnvVar> list = Arrays.asList(
                createSimpleEnvVar("VAR1", "value1"),
                createFileEnvVar(),
                createSensitiveEnvVar()
        );

        DeploymentMetadata metadata = new DeploymentMetadata();
        metadata.setEnvs(Arrays.asList(
                createEnvVarDefinition("VAR1", "value1", EnvVarMountType.CONTENT),
                createFileEnvVarDefinition("file.txt"),
                createEnvVarDefinition("SECRET_VAR", "value", EnvVarMountType.SECURE_CONTENT)
        ));
        
        assertThat(EnvVarChangeDetector.areEnvsChanged(list, metadata)).isFalse();
    }

    @Test
    void testNullMountType() {
        List<EnvVar> list = Collections.singletonList(createSimpleEnvVar("VAR1", "value1"));
        
        DeploymentMetadata metadata = new DeploymentMetadata();
        EnvVarDefinition definition = createEnvVarDefinition("VAR1", "value1", null);
        metadata.setEnvs(Collections.singletonList(definition));
        
        assertThat(EnvVarChangeDetector.areEnvsChanged(list, metadata)).isTrue();
    }

    @Test
    void testMountTypeCompatibilitySimpleEnvVar() {
        List<EnvVar> list = Collections.singletonList(createSimpleEnvVar("VAR1", "value1"));
        
        DeploymentMetadata metadata = new DeploymentMetadata();
        metadata.setEnvs(Collections.singletonList(
                createEnvVarDefinition("VAR1", "value1", EnvVarMountType.CONTENT)
        ));
        
        assertThat(EnvVarChangeDetector.areEnvsChanged(list, metadata)).isFalse();

        metadata.setEnvs(Collections.singletonList(
                createEnvVarDefinition("VAR1", "value1", EnvVarMountType.SECURE_CONTENT)
        ));

        assertThat(EnvVarChangeDetector.areEnvsChanged(list, metadata)).isTrue();

        metadata.setEnvs(Collections.singletonList(
                createEnvVarDefinition("VAR1", "value1", EnvVarMountType.SECURE_FILE)
        ));

        assertThat(EnvVarChangeDetector.areEnvsChanged(list, metadata)).isTrue();
    }

    @Test
    void testMountTypeCompatibilitySensitiveEnvVar() {
        List<EnvVar> list = Collections.singletonList(createSensitiveEnvVar());
        
        DeploymentMetadata metadata = new DeploymentMetadata();
        metadata.setEnvs(Collections.singletonList(
                createEnvVarDefinition("SECRET_VAR", "value", EnvVarMountType.SECURE_CONTENT)
        ));
        
        assertThat(EnvVarChangeDetector.areEnvsChanged(list, metadata)).isFalse();

        metadata.setEnvs(Collections.singletonList(
                createEnvVarDefinition("SECRET_VAR", "value", EnvVarMountType.CONTENT)
        ));

        assertThat(EnvVarChangeDetector.areEnvsChanged(list, metadata)).isTrue();

        metadata.setEnvs(Collections.singletonList(
                createEnvVarDefinition("SECRET_VAR", "value", EnvVarMountType.SECURE_FILE)
        ));

        assertThat(EnvVarChangeDetector.areEnvsChanged(list, metadata)).isTrue();
    }

    @Test
    void testSensitiveFileEnvVar() {
        List<EnvVar> list = Collections.singletonList(createSensitiveFileEnvVar());

        DeploymentMetadata metadata = new DeploymentMetadata();
        EnvVarDefinition definition = new EnvVarDefinition();
        definition.setName("SECRET_FILE_VAR");
        definition.setValue(new FileEnvVarValue("secret-file.txt", "secret content"));
        definition.setMountType(EnvVarMountType.SECURE_FILE);
        definition.setDescription("Test description");
        metadata.setEnvs(Collections.singletonList(definition));

        assertThat(EnvVarChangeDetector.areEnvsChanged(list, metadata)).isFalse();

        definition.setMountType(EnvVarMountType.CONTENT);
        assertThat(EnvVarChangeDetector.areEnvsChanged(list, metadata)).isTrue();

        definition.setMountType(EnvVarMountType.SECURE_CONTENT);
        assertThat(EnvVarChangeDetector.areEnvsChanged(list, metadata)).isTrue();

        definition.setMountType(EnvVarMountType.SECURE_FILE);
        definition.setValue(new FileEnvVarValue("different-file.txt", "secret content"));
        assertThat(EnvVarChangeDetector.areEnvsChanged(list, metadata)).isTrue();
    }

    @Test
    void testNullEnvVarValue() {
        SimpleEnvVar envVar = new SimpleEnvVar();
        envVar.setName("VAR1");
        envVar.setValue(null);
        List<EnvVar> list = Collections.singletonList(envVar);

        DeploymentMetadata metadata = new DeploymentMetadata();
        EnvVarDefinition definition = new EnvVarDefinition();
        definition.setName("VAR1");
        definition.setValue(null);
        definition.setMountType(EnvVarMountType.CONTENT);
        metadata.setEnvs(Collections.singletonList(definition));

        assertThat(EnvVarChangeDetector.areEnvsChanged(list, metadata)).isFalse();

        definition.setValue(new SimpleEnvVarValue("value"));
        assertThat(EnvVarChangeDetector.areEnvsChanged(list, metadata)).isTrue();

        envVar.setValue(new SimpleEnvVarValue("value"));
        definition.setValue(null);
        assertThat(EnvVarChangeDetector.areEnvsChanged(list, metadata)).isTrue();
    }

    private SimpleEnvVar createSimpleEnvVar(String name, String value) {
        SimpleEnvVar envVar = new SimpleEnvVar();
        envVar.setName(name);
        if (value != null) {
            envVar.setValue(new SimpleEnvVarValue(value));
        }
        return envVar;
    }

    private EnvVar createFileEnvVar() {
        SimpleEnvVar envVar = new SimpleEnvVar();
        envVar.setName("FILE_VAR");
        envVar.setValue(new FileEnvVarValue("file.txt", "content"));
        return envVar;
    }

    private SensitiveEnvVar createSensitiveEnvVar() {
        SensitiveEnvVar envVar = new SensitiveEnvVar();
        envVar.setName("SECRET_VAR");
        envVar.setValue(new SimpleEnvVarValue("value"));
        envVar.setK8sSecretName("secret");
        envVar.setK8sSecretKey("key");
        return envVar;
    }
    
    private SensitiveFileEnvVar createSensitiveFileEnvVar() {
        SensitiveFileEnvVar envVar = new SensitiveFileEnvVar();
        envVar.setName("SECRET_FILE_VAR");
        envVar.setValue(new FileEnvVarValue("secret-file.txt", "secret content"));
        envVar.setK8sSecretName("secret");
        envVar.setK8sSecretKey("key");
        return envVar;
    }

    private EnvVarDefinition createEnvVarDefinition(String name, String value, EnvVarMountType mountType) {
        EnvVarDefinition definition = new EnvVarDefinition();
        definition.setName(name);
        definition.setValue(new SimpleEnvVarValue(value));
        definition.setMountType(mountType);
        definition.setDescription("Test description");
        return definition;
    }

    private EnvVarDefinition createFileEnvVarDefinition(String fileName) {
        EnvVarDefinition definition = new EnvVarDefinition();
        definition.setName("FILE_VAR");
        definition.setValue(new FileEnvVarValue(fileName, "content"));
        definition.setMountType(EnvVarMountType.CONTENT);
        definition.setDescription("Test description");
        return definition;
    }
}