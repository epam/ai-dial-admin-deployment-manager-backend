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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class EnvVarChangeDetectorTest {

    @Test
    void testNullMetadata() {
        List<EnvVar> list = Collections.singletonList(createSimpleEnvVar("VAR1", "value1"));
        assertTrue(EnvVarChangeDetector.areEnvsChanged(list, null));
        assertFalse(EnvVarChangeDetector.areEnvsChanged(null, null));
        assertFalse(EnvVarChangeDetector.areEnvsChanged(new ArrayList<>(), null));
    }

    @Test
    void testNullEnvsList() {
        DeploymentMetadata metadata = new DeploymentMetadata();
        metadata.setEnvs(Collections.singletonList(createEnvVarDefinition("VAR1", "value1", EnvVarMountType.CONTENT)));
        assertTrue(EnvVarChangeDetector.areEnvsChanged(null, metadata));
        assertFalse(EnvVarChangeDetector.areEnvsChanged(new ArrayList<>(), new DeploymentMetadata(null)));
        assertFalse(EnvVarChangeDetector.areEnvsChanged(null, new DeploymentMetadata(new ArrayList<>())));
    }

    @Test
    void testDifferentSizesWithMetadata() {
        List<EnvVar> list = Collections.singletonList(createSimpleEnvVar("VAR1", "value1"));

        DeploymentMetadata metadata = new DeploymentMetadata();
        metadata.setEnvs(Arrays.asList(
                createEnvVarDefinition("VAR1", "value1", EnvVarMountType.CONTENT),
                createEnvVarDefinition("VAR2", "value2", EnvVarMountType.CONTENT)
        ));

        assertTrue(EnvVarChangeDetector.areEnvsChanged(list, metadata));
    }

    @Test
    void testDifferentKeysWithMetadata() {
        List<EnvVar> list = Collections.singletonList(createSimpleEnvVar("VAR1", "value1"));

        DeploymentMetadata metadata = new DeploymentMetadata();
        metadata.setEnvs(Collections.singletonList(
                createEnvVarDefinition("VAR2", "value1", EnvVarMountType.CONTENT)
        ));

        assertTrue(EnvVarChangeDetector.areEnvsChanged(list, metadata));
    }

    @Test
    void testSameKeysButDifferentValuesWithMetadata() {
        List<EnvVar> list = Collections.singletonList(createSimpleEnvVar("VAR1", "value1"));

        DeploymentMetadata metadata = new DeploymentMetadata();
        metadata.setEnvs(Collections.singletonList(
                createEnvVarDefinition("VAR1", "value2", EnvVarMountType.CONTENT)
        ));

        assertTrue(EnvVarChangeDetector.areEnvsChanged(list, metadata));
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

        assertFalse(EnvVarChangeDetector.areEnvsChanged(list, metadata));
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

        assertFalse(EnvVarChangeDetector.areEnvsChanged(list, metadata));
    }

    @Test
    void testFileEnvVarValueWithMetadata() {
        List<EnvVar> list = Collections.singletonList(createFileEnvVar());

        DeploymentMetadata metadata = new DeploymentMetadata();
        metadata.setEnvs(Collections.singletonList(
                createFileEnvVarDefinition("file.txt")
        ));

        assertFalse(EnvVarChangeDetector.areEnvsChanged(list, metadata));

        // Different file name
        metadata.setEnvs(Collections.singletonList(
                createFileEnvVarDefinition("different.txt")
        ));

        assertTrue(EnvVarChangeDetector.areEnvsChanged(list, metadata));
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
        
        assertFalse(EnvVarChangeDetector.areEnvsChanged(list, metadata));
    }
    
    @Test
    void testNullMountType() {
        List<EnvVar> list = Collections.singletonList(createSimpleEnvVar("VAR1", "value1"));
        
        DeploymentMetadata metadata = new DeploymentMetadata();
        EnvVarDefinition definition = createEnvVarDefinition("VAR1", "value1", null);
        metadata.setEnvs(Collections.singletonList(definition));
        
        assertTrue(EnvVarChangeDetector.areEnvsChanged(list, metadata));
    }
    
    @Test
    void testMountTypeCompatibilitySimpleEnvVar() {
        List<EnvVar> list = Collections.singletonList(createSimpleEnvVar("VAR1", "value1"));
        
        DeploymentMetadata metadata = new DeploymentMetadata();
        metadata.setEnvs(Collections.singletonList(
                createEnvVarDefinition("VAR1", "value1", EnvVarMountType.CONTENT)
        ));
        
        assertFalse(EnvVarChangeDetector.areEnvsChanged(list, metadata));
        
        metadata.setEnvs(Collections.singletonList(
                createEnvVarDefinition("VAR1", "value1", EnvVarMountType.SECURE_CONTENT)
        ));
        
        assertTrue(EnvVarChangeDetector.areEnvsChanged(list, metadata));
        
        metadata.setEnvs(Collections.singletonList(
                createEnvVarDefinition("VAR1", "value1", EnvVarMountType.SECURE_FILE)
        ));
        
        assertTrue(EnvVarChangeDetector.areEnvsChanged(list, metadata));
    }
    
    @Test
    void testMountTypeCompatibilitySensitiveEnvVar() {
        List<EnvVar> list = Collections.singletonList(createSensitiveEnvVar());
        
        DeploymentMetadata metadata = new DeploymentMetadata();
        metadata.setEnvs(Collections.singletonList(
                createEnvVarDefinition("SECRET_VAR", "value", EnvVarMountType.SECURE_CONTENT)
        ));
        
        assertFalse(EnvVarChangeDetector.areEnvsChanged(list, metadata));
        
        metadata.setEnvs(Collections.singletonList(
                createEnvVarDefinition("SECRET_VAR", "value", EnvVarMountType.CONTENT)
        ));
        
        assertTrue(EnvVarChangeDetector.areEnvsChanged(list, metadata));
        
        metadata.setEnvs(Collections.singletonList(
                createEnvVarDefinition("SECRET_VAR", "value", EnvVarMountType.SECURE_FILE)
        ));
        
        assertTrue(EnvVarChangeDetector.areEnvsChanged(list, metadata));
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

        assertFalse(EnvVarChangeDetector.areEnvsChanged(list, metadata));

        definition.setMountType(EnvVarMountType.CONTENT);
        assertTrue(EnvVarChangeDetector.areEnvsChanged(list, metadata));

        definition.setMountType(EnvVarMountType.SECURE_CONTENT);
        assertTrue(EnvVarChangeDetector.areEnvsChanged(list, metadata));

        definition.setMountType(EnvVarMountType.SECURE_FILE);
        definition.setValue(new FileEnvVarValue("different-file.txt", "secret content"));
        assertTrue(EnvVarChangeDetector.areEnvsChanged(list, metadata));
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

        assertFalse(EnvVarChangeDetector.areEnvsChanged(list, metadata));

        definition.setValue(new SimpleEnvVarValue("value"));
        assertTrue(EnvVarChangeDetector.areEnvsChanged(list, metadata));

        envVar.setValue(new SimpleEnvVarValue("value"));
        definition.setValue(null);
        assertTrue(EnvVarChangeDetector.areEnvsChanged(list, metadata));
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