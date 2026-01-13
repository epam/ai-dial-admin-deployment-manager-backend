package com.epam.aidial.deployment.manager.kubernetes.informer.registration;

import com.epam.aidial.deployment.manager.kubernetes.informer.handler.AbstractResourceEventHandler;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.client.informers.SharedIndexInformer;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public abstract class AbstractInformerRegistration<T extends HasMetadata> implements InformerRegistration {

    protected final AbstractResourceEventHandler<T> eventHandler;

    private SharedIndexInformer<T> informer;

    protected AbstractInformerRegistration(AbstractResourceEventHandler<T> eventHandler) {
        this.eventHandler = eventHandler;
    }

    @Override
    public void start() {
        log.info("Starting {} informer", getResourceType());
        informer = createInformer();
        informer.addEventHandler(eventHandler);
        informer.exceptionHandler((b, t) -> {
            log.info("Exception in {} informer", getResourceType(), t);
            return true;
        });
        informer.run();
        log.info("{} informer started successfully", getResourceType());
    }

    @Override
    public void stop() {
        if (informer != null) {
            log.info("Stopping {} informer", getResourceType());
            informer.stop();
            informer.stopped().toCompletableFuture().join();
            informer = null;
            log.info("{} informer stopped", getResourceType());
        }
    }

    protected abstract SharedIndexInformer<T> createInformer();

}

