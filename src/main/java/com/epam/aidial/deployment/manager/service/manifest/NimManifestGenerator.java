package com.epam.aidial.deployment.manager.service.manifest;

import com.epam.aidial.deployment.manager.configuration.AppProperties;
import com.epam.aidial.deployment.manager.configuration.logging.LogExecution;
import com.epam.aidial.deployment.manager.model.Resources;
import com.epam.aidial.deployment.manager.model.SensitiveEnvVar;
import com.epam.aidial.deployment.manager.model.SimpleEnvVar;
import com.epam.aidial.deployment.manager.model.probe.ProbeProperties;
import com.epam.aidial.deployment.manager.utils.K8sNamingUtils;
import com.epam.aidial.deployment.manager.utils.mapping.MappingChain;
import com.epam.aidial.deployment.manager.utils.mapping.NimMappers;
import com.google.cloud.tools.jib.api.ImageReference;
import com.networknt.schema.utils.StringUtils;
import com.nvidia.apps.v1alpha1.NIMService;
import com.nvidia.apps.v1alpha1.NIMServiceSpec;
import com.nvidia.apps.v1alpha1.nimservicespec.Env;
import com.nvidia.apps.v1alpha1.nimservicespec.Expose;
import com.nvidia.apps.v1alpha1.nimservicespec.env.ValueFrom;
import com.nvidia.apps.v1alpha1.nimservicespec.env.valuefrom.SecretKeyRef;
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
import org.jetbrains.annotations.Nullable;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@LogExecution
public class NimManifestGenerator extends DeployableManifestGenerator {

    private final NimProbeConverter nimProbeConverter;

    public NimManifestGenerator(AppProperties appconfig,
                                NimProbeConverter nimProbeConverter) {
        super(appconfig);
        this.nimProbeConverter = nimProbeConverter;
    }

    @SneakyThrows
    public NIMService serviceConfig(
            String name,
            List<SimpleEnvVar> envs,
            List<SensitiveEnvVar> sensitiveEnv,
            Resources resources,
            String imageName,
            int containerPort,
            @Nullable Integer containerGrpcPort,
            @Nullable ProbeProperties probeProperties,
            boolean useExternalUrl,
            @Nullable String clusterHost
    ) {
        if (useExternalUrl && StringUtils.isBlank(clusterHost)) {
            throw new IllegalArgumentException("External NIM URL is enabled but cluster host is not configured");
        }

        var nimServiceName = K8sNamingUtils.generateMcpPrefixedName(name);
        var config = createBaseManifestChain(
                appConfig::cloneNimServiceConfig,
                chain -> chain.get(NimMappers.SERVICE_METADATA_FIELD),
                nimServiceName
        );

        var specChain = config.get(NimMappers.SERVICE_SPEC_FIELD);
        configureImage(specChain, imageName);

        var envListMapper = specChain.getList(NimMappers.SERVICE_SPEC_ENVS_FIELD, NimMappers.ENV_VAR_NAME);
        applySimpleEnvs(envListMapper, envs, Env::setValue);
        applySensitiveEnvs(envListMapper, sensitiveEnv, Env::setValueFrom, this::buildNimSecretRef);

        var resourceLimitsChain = specChain.get(NimMappers.SERVICE_SPEC_RESOURCES_FIELD)
                .get(NimMappers.RESOURCES_LIMITS_FIELD);
        var resourceRequestsChain = specChain.get(NimMappers.SERVICE_SPEC_RESOURCES_FIELD)
                .get(NimMappers.RESOURCES_REQUESTS_FIELD);

        applyResourceMap(resourceLimitsChain.data(), resources.getLimits(), IntOrString::new);
        applyResourceMap(resourceRequestsChain.data(), resources.getRequests(), IntOrString::new);

        var exposeChain = specChain.get(NimMappers.SERVICE_SPEC_EXPOSE_FIELD);
        if (useExternalUrl) {
            applyExposeIngress(exposeChain, nimServiceName, clusterHost, containerPort);
        } else {
            applyExposeService(exposeChain, containerPort, containerGrpcPort);
        }

        applyStartupProbe(name, specChain, probeProperties);

        return config.data();
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
        var ingressChain = new MappingChain<>(appConfig.getNimServiceExposeIngressConfig());
        var ingressSpecChain = ingressChain.get(NimMappers.INGRESS_SPEC_FIELD);
        var ingressSpec = ingressSpecChain.data();

        ingressSpec.setTls(List.of(buildTls(nimServiceName, clusterHost)));
        ingressSpec.setRules(List.of(buildRule(nimServiceName, clusterHost, httpPort)));

        exposeChain.data().setIngress(ingressChain.data());
    }

    private Tls buildTls(String nimServiceName, String clusterHost) {
        var tls = new Tls();
        tls.setHosts(List.of(nimServiceName + "." + clusterHost));
        tls.setSecretName(nimServiceName + "-tls-secret");
        return tls;
    }

    private Rules buildRule(String nimServiceName, String clusterHost, int httpPort) {
        var rule = new Rules();
        rule.setHost(nimServiceName + "." + clusterHost);
        var http = new Http();
        var path = new Paths();
        path.setPath("/");
        path.setPathType("Prefix");
        var backend = new Backend();
        var backendService = new Service();
        backendService.setName(nimServiceName);
        var port = new Port();
        port.setNumber(httpPort);
        backendService.setPort(port);
        backend.setService(backendService);
        path.setBackend(backend);
        http.setPaths(List.of(path));
        rule.setHttp(http);
        return rule;
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

    @SneakyThrows
    private void configureImage(MappingChain<NIMServiceSpec> specChain, String imageName) {
        var serviceImage = specChain.get(NimMappers.SERVICE_SPEC_IMAGE_FIELD).data();
        var imageReference = ImageReference.parse(imageName);
        serviceImage.setRepository(imageReference.getRegistry() + "/" + imageReference.getRepository());
        serviceImage.setTag(imageReference.getTag().orElse("latest"));
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
