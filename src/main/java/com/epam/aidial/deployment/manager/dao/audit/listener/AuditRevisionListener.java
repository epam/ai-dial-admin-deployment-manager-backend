package com.epam.aidial.deployment.manager.dao.audit.listener;

import com.epam.aidial.deployment.manager.dao.audit.entity.AuditActivityEntity;
import com.epam.aidial.deployment.manager.dao.audit.entity.AuditRevisionEntity;
import com.epam.aidial.deployment.manager.dao.audit.mapper.AuditActivityMapper;
import com.epam.aidial.deployment.manager.model.audit.ActivityResourceType;
import com.epam.aidial.deployment.manager.model.audit.ActivityType;
import com.epam.aidial.deployment.manager.service.security.SecurityClaimsExtractor;
import com.epam.aidial.deployment.manager.transaction.timestamp.TransactionTimestampContext;
import com.github.f4b6a3.uuid.UuidCreator;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.envers.EntityTrackingRevisionListener;
import org.hibernate.envers.RevisionType;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
public class AuditRevisionListener implements EntityTrackingRevisionListener, ApplicationContextAware {

    private static volatile SecurityClaimsExtractor securityClaimsExtractor;
    private static volatile TransactionTimestampContext transactionTimestampContext;
    private static volatile AuditActivityMapper auditActivityMapper;

    // ThreadLocal is not explicitly cleared after transaction completion because Hibernate Envers
    // does not provide an "after flush" callback. The map is replaced on each newRevision() call,
    // so stale references are limited to a single small HashMap per pooled thread between transactions.
    private static final ThreadLocal<Map<String, AuditActivityEntity>> DEDUP_MAP = ThreadLocal.withInitial(HashMap::new);

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        securityClaimsExtractor = applicationContext.getBeanProvider(SecurityClaimsExtractor.class).getIfAvailable();
        transactionTimestampContext = applicationContext.getBeanProvider(TransactionTimestampContext.class).getIfAvailable();
        auditActivityMapper = applicationContext.getBeanProvider(AuditActivityMapper.class).getIfAvailable();
    }

    @Override
    public void newRevision(Object revisionEntity) {
        AuditRevisionEntity rev = (AuditRevisionEntity) revisionEntity;
        try {
            String author = securityClaimsExtractor != null ? securityClaimsExtractor.getAuthor() : null;
            if (author != null) {
                rev.setAuthor(author);
                rev.setEmail(securityClaimsExtractor.getEmail());
            } else {
                Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
                rev.setAuthor(authentication != null ? "unknown" : "system");
            }
        } catch (Exception e) {
            log.debug("Unable to extract security claims for audit revision", e);
        }
        try {
            rev.setTimestamp(transactionTimestampContext.getTimestamp());
        } catch (Exception e) {
            log.debug("Unable to get transaction timestamp, using current time", e);
            rev.setTimestamp(System.currentTimeMillis());
        }
        DEDUP_MAP.set(new HashMap<>());
    }

    @Override
    public void entityChanged(Class entityClass, String entityName, Object entityId,
                              RevisionType revisionType, Object revisionEntity) {
        AuditRevisionEntity rev = (AuditRevisionEntity) revisionEntity;
        ActivityResourceType resourceType;
        try {
            resourceType = auditActivityMapper.mapResourceType(entityClass);
        } catch (IllegalArgumentException e) {
            log.trace("Skipping unmapped entity class: {}", entityClass.getName());
            return;
        }

        ActivityType activityType = auditActivityMapper.mapActivityType(revisionType);
        String resourceId = entityId != null ? entityId.toString() : null;
        String dedupKey = resourceType + ":" + resourceId;

        Map<String, AuditActivityEntity> dedupMap = DEDUP_MAP.get();
        if (dedupMap == null) {
            dedupMap = new HashMap<>();
            DEDUP_MAP.set(dedupMap);
        }

        AuditActivityEntity existing = dedupMap.get(dedupKey);
        if (existing != null) {
            if (activityType == ActivityType.Create || activityType == ActivityType.Delete) {
                rev.getActivities().remove(existing);
            } else {
                return;
            }
        }

        AuditActivityEntity activity = AuditActivityEntity.builder()
                .activityId(UuidCreator.getTimeOrderedEpoch())
                .activityType(activityType)
                .resourceType(resourceType)
                .resourceId(resourceId)
                .epochTimestampMs(rev.getTimestamp())
                .initiatedAuthor(rev.getAuthor())
                .initiatedEmail(rev.getEmail())
                .revision(rev.getId())
                .build();

        rev.getActivities().add(activity);
        dedupMap.put(dedupKey, activity);
    }
}
