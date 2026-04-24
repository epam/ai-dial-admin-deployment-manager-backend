package com.epam.aidial.deployment.manager.service.manifest;

import com.epam.aidial.deployment.manager.configuration.AppProperties;
import com.epam.aidial.deployment.manager.configuration.NimDeployProperties;
import com.epam.aidial.deployment.manager.configuration.logging.LogExecution;
import com.epam.aidial.deployment.manager.kubernetes.knative.KnativeAnnotations;
import com.epam.aidial.deployment.manager.model.Resources;
import com.epam.aidial.deployment.manager.model.Scaling;
import com.epam.aidial.deployment.manager.model.SensitiveEnvVar;
import com.epam.aidial.deployment.manager.model.SimpleEnvVar;
import com.epam.aidial.deployment.manager.model.probe.ProbeProperties;
import com.epam.aidial.deployment.manager.utils.mapping.ListMapper;
import com.epam.aidial.deployment.manager.utils.mapping.MappingChain;
import com.epam.aidial.deployment.manager.utils.mapping.NimMappers;
import com.google.cloud.tools.jib.api.ImageReference;
import com.nvidia.apps.v1alpha1.NIMService;
import com.nvidia.apps.v1alpha1.NIMServiceSpec;
import com.nvidia.apps.v1alpha1.nimservicespec.Env;
import com.nvidia.apps.v1alpha1.nimservicespec.Expose;
import com.nvidia.apps.v1alpha1.nimservicespec.env.ValueFrom;
import com.nvidia.apps.v1alpha1.nimservicespec.env.valuefrom.SecretKeyRef;
import com.nvidia.apps.v1alpha1.nimservicespec.expose.Router;
import com.nvidia.apps.v1alpha1.nimservicespec.expose.ingress.spec.Rules;
import com.nvidia.apps.v1alpha1.nimservicespec.expose.ingress.spec.Tls;
import com.nvidia.apps.v1alpha1.nimservicespec.expose.ingress.spec.rules.Http;
import com.nvidia.apps.v1alpha1.nimservicespec.expose.ingress.spec.rules.http.Paths;
import com.nvidia.apps.v1alpha1.nimservicespec.expose.ingress.spec.rules.http.paths.Backend;
import com.nvidia.apps.v1alpha1.nimservicespec.expose.ingress.spec.rules.http.paths.backend.Service;
import com.nvidia.apps.v1alpha1.nimservicespec.expose.ingress.spec.rules.http.paths.backend.service.Port;
import io.fabric8.kubernetes.api.model.IntOrString;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.MapUtils;
import org.jetbrains.annotations.Nullable;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Slf4j
@Component
@LogExecution
public class NimManifestGenerator extends DeployableManifestGenerator {

    private static final String NIM_SERVED_MODEL_NAME_ENV = "NIM_SERVED_MODEL_NAME";
    private static final String TLS_SECRET_NAME_SUFFIX = "-tls-secret";
    private static final Scaling DEFAULT_SCALING = new Scaling(1, 1, null, null);

    private final NimProbeConverter nimProbeConverter;
    private final ProgressDeadlineCalculator progressDeadlineCalculator;
    private final NimDeployProperties nimDeployProperties;

    public NimManifestGenerator(AppProperties appconfig,
                                NimProbeConverter nimProbeConverter,
                                ProgressDeadlineCalculator progressDeadlineCalculator,
                                NimDeployProperties nimDeployProperties) {
        super(appconfig);
        this.nimProbeConverter = nimProbeConverter;
        this.progressDeadlineCalculator = progressDeadlineCalculator;
        this.nimDeployProperties = nimDeployProperties;
    }

    @SneakyThrows
    public NIMService serviceConfig(
            String name,
            String serviceName,
            List<SimpleEnvVar> envs,
            List<SensitiveEnvVar> sensitiveEnv,
            Resources resources,
            String imageName,
            int containerPort,
            @Nullable Integer containerGrpcPort,
            @Nullable String storageSize,
            @Nullable Scaling scaling,
            @Nullable ProbeProperties probeProperties,
            int startupTimeoutSec,
            @Nullable List<String> command,
            @Nullable List<String> args,
            @Nullable Map<String, String> nodePoolLabels
    ) {
        boolean kserveMode = nimDeployProperties.isKserveModeEnabled();
        boolean useExternalUrl = !nimDeployProperties.isUseClusterInternalUrl();

        var config = createBaseManifestChain(
                appConfig::cloneNimServiceConfig,
                chain -> chain.get(NimMappers.SERVICE_METADATA_FIELD),
                serviceName
        );

        var specChain = config.get(NimMappers.SERVICE_SPEC_FIELD);
        configureImage(specChain, imageName);

        var envListMapper = specChain.getList(NimMappers.SERVICE_SPEC_ENVS_FIELD, NimMappers.ENV_VAR_NAME);
        applySimpleEnvs(envListMapper, envs, Env::setValue);
        applySensitiveEnvs(envListMapper, sensitiveEnv, Env::setValueFrom, this::buildNimSecretRef);
        setServedModelNameIfNotSet(name, envs, sensitiveEnv, envListMapper);

        var resourceLimitsChain = specChain.get(NimMappers.SERVICE_SPEC_RESOURCES_FIELD)
                .get(NimMappers.RESOURCES_LIMITS_FIELD);
        var resourceRequestsChain = specChain.get(NimMappers.SERVICE_SPEC_RESOURCES_FIELD)
                .get(NimMappers.RESOURCES_REQUESTS_FIELD);

        applyResourceMap(resourceLimitsChain.data(), resources.getLimits(), IntOrString::new);
        applyResourceMap(resourceRequestsChain.data(), resources.getRequests(), IntOrString::new);

        applyStorageSize(specChain, storageSize);

        var exposeChain = specChain.get(NimMappers.SERVICE_SPEC_EXPOSE_FIELD);
        applyExposeService(exposeChain, containerPort, containerGrpcPort);

        if (kserveMode) {
            specChain.data().setInferencePlatform(NIMServiceSpec.InferencePlatform.KSERVE);
            exposeChain.data().setRouter(new Router());
            applyScaling(name, scaling, config);
        } else {
            if (scaling != null) {
                log.warn("NIM deployment '{}': 'scaling' is ignored in legacy (standalone) mode. "
                        + "Set app.nim.deploy.kserve-mode-enabled=true to enable Knative autoscaling.", name);
            }
            if (useExternalUrl) {
                applyExposeIngress(exposeChain, serviceName, nimDeployProperties.getClusterHost(), containerPort);
            }
        }

        if (command != null) {
            specChain.data().setCommand(command);
        }
        if (args != null) {
            specChain.data().setArgs(args);
        }

        applyStartupProbe(name, specChain, probeProperties);
        applyProgressDeadline(probeProperties, startupTimeoutSec, config);

        if (MapUtils.isNotEmpty(nodePoolLabels)) {
            specChain.data().setNodeSelector(nodePoolLabels);
        }

        return config.data();
    }

    private void applyStorageSize(MappingChain<NIMServiceSpec> specChain, @Nullable String storageSize) {
        if (storageSize != null) {
            specChain.get(NimMappers.SERVICE_SPEC_STORAGE_FIELD)
                    .get(NimMappers.STORAGE_PVC_FIELD)
                    .data()
                    .setSize(storageSize);
        }
    }

    private void applyExposeService(MappingChain<Expose> exposeChain, int httpPort, @Nullable Integer containerGrpcPort) {
        var serviceChain = exposeChain.get(NimMappers.EXPOSE_SERVICE_FIELD);
        var service = serviceChain.data();
        service.setPort(httpPort);
        if (containerGrpcPort != null) {
            service.setGrpcPort(containerGrpcPort);
        }
    }

    private void applyExposeIngress(MappingChain<Expose> exposeChain, String nimServiceName, String clusterHost, int httpPort) {
        var host = nimServiceName + "." + clusterHost;

        var ingressChain = new MappingChain<>(appConfig.cloneNimServiceExposeIngressConfig());
        var ingressSpec = ingressChain.get(NimMappers.INGRESS_SPEC_FIELD).data();
        ingressSpec.setTls(List.of(buildTls(nimServiceName, host)));
        ingressSpec.setRules(List.of(buildRule(nimServiceName, host, httpPort)));

        exposeChain.data().setIngress(ingressChain.data());
    }

    private Tls buildTls(String nimServiceName, String host) {
        var tls = new Tls();
        tls.setHosts(List.of(host));
        tls.setSecretName(nimServiceName + TLS_SECRET_NAME_SUFFIX);
        return tls;
    }

    private Rules buildRule(String nimServiceName, String host, int httpPort) {
        var path = new Paths();
        path.setPath("/");
        path.setPathType("Prefix");
        path.setBackend(buildBackend(nimServiceName, httpPort));

        var http = new Http();
        http.setPaths(List.of(path));

        var rule = new Rules();
        rule.setHost(host);
        rule.setHttp(http);
        return rule;
    }

    private Backend buildBackend(String nimServiceName, int httpPort) {
        var servicePort = new Port();
        servicePort.setNumber(httpPort);

        var service = new Service();
        service.setName(nimServiceName);
        service.setPort(servicePort);

        var backend = new Backend();
        backend.setService(service);
        return backend;
    }

    private void applyScaling(String name, @Nullable Scaling scaling, MappingChain<NIMService> config) {
        log.debug("Applying scaling for NIM deployment '{}': {}", name, scaling);
        var annotations = config.get(NimMappers.SERVICE_METADATA_FIELD)
                .get(NimMappers.METADATA_ANNOTATIONS_FIELD).data();
        applyScalingAnnotations(name, scaling != null ? scaling : DEFAULT_SCALING, annotations);
    }

    private void applyStartupProbe(String name,
                                   MappingChain<NIMServiceSpec> specChain,
                                   @Nullable ProbeProperties deploymentProbeProperties) {
        var probe = nimProbeConverter.toNimStartupProbe(deploymentProbeProperties);
        if (probe != null) {
            log.debug("Applying startup probe for NIM deployment '{}': {}", name, probe);
            specChain.data().setStartupProbe(probe);
        }
    }

    private void applyProgressDeadline(@Nullable ProbeProperties probeProperties,
                                       int startupTimeoutSec,
                                       MappingChain<NIMService> config) {
        var progressDeadline = progressDeadlineCalculator.compute(probeProperties, startupTimeoutSec);
        var annotations = config.get(NimMappers.SERVICE_METADATA_FIELD)
                .get(NimMappers.METADATA_ANNOTATIONS_FIELD).data();
        annotations.put(KnativeAnnotations.PROGRESS_DEADLINE, progressDeadline);
    }

    @SneakyThrows
    private void configureImage(MappingChain<NIMServiceSpec> specChain, String imageName) {
        var serviceImage = specChain.get(NimMappers.SERVICE_SPEC_IMAGE_FIELD).data();
        var imageReference = ImageReference.parse(imageName);
        serviceImage.setRepository(imageReference.getRegistry() + "/" + imageReference.getRepository());
        serviceImage.setTag(imageReference.getTag().orElse("latest"));
    }

    private void setServedModelNameIfNotSet(String deploymentName,
                                            List<SimpleEnvVar> simpleEnvs,
                                            List<SensitiveEnvVar> sensitiveEnvs,
                                            ListMapper<Env> envListMapper) {
        boolean alreadySet = simpleEnvs.stream().anyMatch(e -> NIM_SERVED_MODEL_NAME_ENV.equals(e.getName()))
                || sensitiveEnvs.stream().anyMatch(e -> NIM_SERVED_MODEL_NAME_ENV.equals(e.getName()));
        if (alreadySet) {
            log.debug("Environment variable {} is already set for NIM deployment '{}', skipping.",
                    NIM_SERVED_MODEL_NAME_ENV, deploymentName);
            return;
        }
        envListMapper.get(NIM_SERVED_MODEL_NAME_ENV).data().setValue(deploymentName);
    }

    private ValueFrom buildNimSecretRef(SensitiveEnvVar env) {
        var secretKeyRef = new SecretKeyRef();
        secretKeyRef.setKey(env.getK8sSecretKey());
        secretKeyRef.setName(env.getK8sSecretName());
        var valueFrom = new ValueFrom();
        valueFrom.setSecretKeyRef(secretKeyRef);
        return valueFrom;
    }

}
