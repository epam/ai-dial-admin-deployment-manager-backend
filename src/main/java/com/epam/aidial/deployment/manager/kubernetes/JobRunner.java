package com.epam.aidial.deployment.manager.kubernetes;

import com.epam.aidial.deployment.manager.cleanup.resource.DisposableResourceManager;
import com.epam.aidial.deployment.manager.cleanup.resource.model.K8sResourceKind;
import com.epam.aidial.deployment.manager.configuration.logging.LogExecution;
import com.epam.aidial.deployment.manager.service.GlobalDomainWhitelistService;
import com.epam.aidial.deployment.manager.service.JobSpecification;
import com.epam.aidial.deployment.manager.service.pipeline.specification.CiliumNetworkPolicyCreator;
import com.epam.aidial.deployment.manager.utils.K8sNamingUtils;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.batch.v1.Job;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;

@Slf4j
@Component
@LogExecution
@RequiredArgsConstructor
public class JobRunner {

    static final String IMAGE_DEFINITION_ID_LABEL = "image-definition-id";
    private static final String JOB_NAME_LABEL = "job-name";

    private final GlobalDomainWhitelistService globalDomainWhitelistService;
    private final CiliumNetworkPolicyCreator ciliumNetworkPolicyCreator;
    private final DisposableResourceManager disposableResourceManager;
    private final K8sClient k8sClient;
    private final PodLogReader logReader;

    @Value("${app.build-namespace}")
    private final String buildNamespace;
    @Value("${app.image-build-timeout-sec}")
    private final int imageBuildTimeoutSec;
    @Value("${app.image-build-stop-timeout-sec}")
    private final int imageBuildStopTimeoutSec;

    public boolean run(
            @NotNull JobSpecification jobSpecification,
            @NotNull JobCallback jobCallback,
            @NotNull UUID groupId,
            @NotNull List<String> containerNames,
            @NotNull List<String> allowedDomains
    ) {
        var jobId = jobSpecification.getJobId();
        var namespace = jobSpecification.getNamespace();
        var client = k8sClient;

        var secrets = jobSpecification.getSecrets();
        disposableResourceManager.saveK8sResources(secrets, K8sResourceKind.SECRET, groupId, namespace);
        secrets.forEach(secret -> {
                    var created = client.createSecret(namespace, secret);
                    log.debug("Created secret '{}'. jobId: '{}'", K8sNamingUtils.extractName(created), jobId);
                }
        );

        var configMaps = jobSpecification.getConfigMaps();
        disposableResourceManager.saveK8sResources(configMaps, K8sResourceKind.CONFIGMAP, groupId, namespace);
        configMaps.forEach(configMap -> {
                    var created = client.createConfigMap(namespace, configMap);
                    log.debug("Created configmap '{}'. jobId: '{}'", K8sNamingUtils.extractName(created), jobId);
                }
        );

        if (ciliumNetworkPolicyCreator.isCiliumNetworkPoliciesEnabled()) {
            createCiliumNetworkPolicy(groupId, allowedDomains, jobId, namespace, client);
        }

        var job = jobSpecification.getJob();
        tagJobWithGroupId(job, groupId);
        disposableResourceManager.saveK8sResources(List.of(job), K8sResourceKind.JOB, groupId, namespace);
        client.createJob(namespace, job);
        log.info("Created build job. jobId: '{}'", jobId);
        jobCallback.onJobPhaseChange(JobPhase.CREATED);

        log.debug("Waiting for job to start. jobId: '{}'", jobId);
        Predicate<Job> jobIsRunning = j -> j == null || JobPhase.fromJob(j)
                .map(s -> s == JobPhase.RUNNING || s.isFinal())
                .orElse(false);
        var runningJob = client.waitJob(namespace, job, jobIsRunning, imageBuildTimeoutSec);
        if (runningJob == null) {
            throw new JobExternallyDeletedException(jobId);
        }
        log.info("Job has started. jobId: '{}'", jobId);
        jobCallback.onJobPhaseChange(JobPhase.RUNNING);

        var jobName = runningJob.getMetadata().getName();
        var pods = client.getJobPods(namespace, jobName);
        var pod = pods.getItems().getFirst();

        log.debug("Waiting for pod to start. jobId: '{}'", jobId);
        Predicate<Pod> podIsRunning = p -> PodPhase.fromPod(p)
                .map(s -> s == PodPhase.RUNNING || s.isFinal())
                .orElse(false);
        client.waitPod(namespace, pod, podIsRunning, imageBuildTimeoutSec);
        log.info("Pod has started. jobId: '{}'", jobId);

        var podName = pod.getMetadata().getName();

        for (String containerName : containerNames) {
            try {
                log.debug("Reading logs from container '{}'. jobId: '{}'", containerName, jobId);
                var containerResource = client.getPodResource(namespace, podName).inContainer(containerName);
                logReader.readLogs(containerResource, jobCallback::onNewLog);
                log.debug("Finished reading logs from container '{}'. jobId: '{}'", containerName, jobId);
            } catch (Exception e) {
                log.warn("Failed to read logs from container '{}'. jobId: '{}'. Error: {}", containerName, jobId, e.getMessage(), e);
                jobCallback.onNewLog(List.of("Warning: Failed to read logs from container '" + containerName + "'. Error: " + e.getMessage()));
            }
        }

        log.debug("Waiting for job to finish. jobId: '{}'", jobId);
        Predicate<Job> jobIsFinished = j -> j == null || JobPhase.fromJob(j)
                .map(JobPhase::isFinal)
                .orElse(false);
        var finishedJob = client.waitJob(namespace, job, jobIsFinished, imageBuildTimeoutSec);
        if (finishedJob == null) {
            throw new JobExternallyDeletedException(jobId);
        }
        var finishedState = JobPhase.fromJobStrictly(finishedJob);
        if (finishedState == JobPhase.SUCCEEDED) {
            log.info("Job has finished successfully. jobId: '{}'", jobId);
            jobCallback.onJobPhaseChange(JobPhase.SUCCEEDED);
        } else {
            log.warn("Job has finished with error. jobId: '{}'", jobId);
            jobCallback.onJobPhaseChange(JobPhase.FAILED);
        }

        return finishedState == JobPhase.SUCCEEDED;
    }

    public void deleteJob(@NotNull UUID groupId) {
        log.info("Deleting Job(s) in namespace '{}' with label {}={} (timeout {}s)",
                buildNamespace, IMAGE_DEFINITION_ID_LABEL, groupId, imageBuildStopTimeoutSec);
        k8sClient.deleteJobsByLabelAndWait(buildNamespace, IMAGE_DEFINITION_ID_LABEL, String.valueOf(groupId), imageBuildStopTimeoutSec);
    }

    private static void tagJobWithGroupId(Job job, UUID groupId) {
        var metadata = job.getMetadata();
        if (metadata.getLabels() == null) {
            metadata.setLabels(new HashMap<>());
        }
        metadata.getLabels().put(IMAGE_DEFINITION_ID_LABEL, String.valueOf(groupId));
    }

    private void createCiliumNetworkPolicy(@NotNull UUID groupId,
                                           @NotNull List<String> allowedDomains,
                                           String jobId,
                                           String namespace,
                                           K8sClient client) {
        log.trace("createCiliumNetworkPolicy. groupId='{}', allowedDomains={}, jobId='{}', namespace='{}'", groupId, allowedDomains, jobId, namespace);

        var globalAllowedDomains = globalDomainWhitelistService.getDomainWhitelist();
        List<String> allAllowedDomains = new ArrayList<>(allowedDomains);
        allAllowedDomains.addAll(globalAllowedDomains);
        log.trace("Combined allowed domains (job-specific + global): {}", allAllowedDomains);

        var ciliumNetworkPolicy = ciliumNetworkPolicyCreator.create(namespace, JOB_NAME_LABEL,
                K8sNamingUtils.generateName(jobId), allAllowedDomains, null);
        disposableResourceManager.saveK8sResources(List.of(ciliumNetworkPolicy), K8sResourceKind.CILIUM_NETWORK_POLICY, groupId, namespace);
        var createdCiliumNetworkPolicy = client.createCiliumNetworkPolicy(namespace, ciliumNetworkPolicy);
        log.debug("Created CiliumNetworkPolicy '{}'. jobId: '{}'", K8sNamingUtils.extractName(createdCiliumNetworkPolicy), jobId);
    }

}
