package com.epam.aidial.deployment.manager.dao.audit.listener;

import com.epam.aidial.deployment.manager.dao.audit.entity.AuditActivityEntity;
import com.epam.aidial.deployment.manager.dao.audit.entity.AuditRevisionEntity;
import com.epam.aidial.deployment.manager.dao.audit.mapper.AuditActivityMapper;
import com.epam.aidial.deployment.manager.dao.entity.deployment.McpDeploymentEntity;
import com.epam.aidial.deployment.manager.model.audit.ActivityResourceType;
import com.epam.aidial.deployment.manager.model.audit.ActivityType;
import com.epam.aidial.deployment.manager.service.security.SecurityClaimsExtractor;
import com.epam.aidial.deployment.manager.transaction.timestamp.TransactionTimestampContext;
import org.hibernate.envers.RevisionType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationContext;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuditRevisionListenerTest {

    @Mock
    private ApplicationContext applicationContext;
    @Mock
    private ObjectProvider<SecurityClaimsExtractor> claimsExtractorProvider;
    @Mock
    private ObjectProvider<TransactionTimestampContext> timestampContextProvider;
    @Mock
    private ObjectProvider<AuditActivityMapper> activityMapperProvider;
    @Mock
    private SecurityClaimsExtractor securityClaimsExtractor;
    @Mock
    private TransactionTimestampContext transactionTimestampContext;

    private final AuditActivityMapper auditActivityMapper = new AuditActivityMapper();

    private AuditRevisionListener listener;

    @BeforeEach
    void setUp() {
        listener = new AuditRevisionListener();

        when(applicationContext.getBeanProvider(SecurityClaimsExtractor.class)).thenReturn(claimsExtractorProvider);
        when(applicationContext.getBeanProvider(TransactionTimestampContext.class)).thenReturn(timestampContextProvider);
        when(applicationContext.getBeanProvider(AuditActivityMapper.class)).thenReturn(activityMapperProvider);

        when(claimsExtractorProvider.getIfAvailable()).thenReturn(securityClaimsExtractor);
        when(timestampContextProvider.getIfAvailable()).thenReturn(transactionTimestampContext);
        when(activityMapperProvider.getIfAvailable()).thenReturn(auditActivityMapper);

        listener.setApplicationContext(applicationContext);
    }

    @AfterEach
    void cleanup() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void newRevision_setsAuthorAndEmail_fromClaimsExtractor() {
        when(securityClaimsExtractor.getAuthor()).thenReturn("testUser");
        when(securityClaimsExtractor.getEmail()).thenReturn("test@example.com");
        when(transactionTimestampContext.getTimestamp()).thenReturn(1700000000000L);

        AuditRevisionEntity rev = new AuditRevisionEntity();
        listener.newRevision(rev);

        assertThat(rev.getAuthor()).isEqualTo("testUser");
        assertThat(rev.getEmail()).isEqualTo("test@example.com");
        assertThat(rev.getTimestamp()).isEqualTo(1700000000000L);
    }

    @Test
    void newRevision_setsAuthorUnknown_whenClaimsReturnsNull_butAuthExists() {
        when(securityClaimsExtractor.getAuthor()).thenReturn(null);
        when(transactionTimestampContext.getTimestamp()).thenReturn(1700000000000L);
        SecurityContextHolder.getContext().setAuthentication(
                new TestingAuthenticationToken("principal", "credentials"));

        AuditRevisionEntity rev = new AuditRevisionEntity();
        listener.newRevision(rev);

        assertThat(rev.getAuthor()).isEqualTo("unknown");
        assertThat(rev.getEmail()).isNull();
    }

    @Test
    void newRevision_setsAuthorSystem_whenNoAuthentication() {
        when(securityClaimsExtractor.getAuthor()).thenReturn(null);
        when(transactionTimestampContext.getTimestamp()).thenReturn(1700000000000L);

        AuditRevisionEntity rev = new AuditRevisionEntity();
        listener.newRevision(rev);

        assertThat(rev.getAuthor()).isEqualTo("system");
    }

    @Test
    void newRevision_setsTimestamp_fromTransactionTimestampContext() {
        when(securityClaimsExtractor.getAuthor()).thenReturn("user");
        when(securityClaimsExtractor.getEmail()).thenReturn("user@test.com");
        when(transactionTimestampContext.getTimestamp()).thenReturn(9999999999999L);

        AuditRevisionEntity rev = new AuditRevisionEntity();
        listener.newRevision(rev);

        assertThat(rev.getTimestamp()).isEqualTo(9999999999999L);
    }

    @Test
    void newRevision_fallsBackToCurrentTime_whenTimestampContextThrows() {
        when(securityClaimsExtractor.getAuthor()).thenReturn("user");
        when(securityClaimsExtractor.getEmail()).thenReturn("user@test.com");
        when(transactionTimestampContext.getTimestamp()).thenThrow(new IllegalStateException("No active transaction"));

        long before = System.currentTimeMillis();
        AuditRevisionEntity rev = new AuditRevisionEntity();
        listener.newRevision(rev);
        long after = System.currentTimeMillis();

        assertThat(rev.getTimestamp()).isBetween(before, after);
    }

    @Test
    void newRevision_handlesClaimsExtractorException_gracefully() {
        when(securityClaimsExtractor.getAuthor()).thenThrow(new RuntimeException("boom"));
        when(transactionTimestampContext.getTimestamp()).thenReturn(1700000000000L);

        AuditRevisionEntity rev = new AuditRevisionEntity();
        listener.newRevision(rev);

        // Should not throw; author stays null from the exception path
        assertThat(rev.getTimestamp()).isEqualTo(1700000000000L);
    }

    @Test
    void entityChanged_createsActivityForMappedEntity() {
        when(securityClaimsExtractor.getAuthor()).thenReturn("testUser");
        when(securityClaimsExtractor.getEmail()).thenReturn("test@example.com");
        when(transactionTimestampContext.getTimestamp()).thenReturn(1700000000000L);

        AuditRevisionEntity rev = new AuditRevisionEntity();
        rev.setId(1);
        listener.newRevision(rev);

        listener.entityChanged(McpDeploymentEntity.class, "McpDeploymentEntity",
                "deploy-123", RevisionType.ADD, rev);

        assertThat(rev.getActivities()).hasSize(1);
        AuditActivityEntity activity = rev.getActivities().iterator().next();
        assertThat(activity.getActivityType()).isEqualTo(ActivityType.Create);
        assertThat(activity.getResourceType()).isEqualTo(ActivityResourceType.McpDeployment);
        assertThat(activity.getResourceId()).isEqualTo("deploy-123");
        assertThat(activity.getInitiatedAuthor()).isEqualTo("testUser");
        assertThat(activity.getInitiatedEmail()).isEqualTo("test@example.com");
        assertThat(activity.getEpochTimestampMs()).isEqualTo(1700000000000L);
        assertThat(activity.getRevision()).isEqualTo(1);
        assertThat(activity.getActivityId()).isNotNull();
    }

    @Test
    void entityChanged_skipsUnmappedEntity() {
        when(securityClaimsExtractor.getAuthor()).thenReturn("user");
        when(securityClaimsExtractor.getEmail()).thenReturn("user@test.com");
        when(transactionTimestampContext.getTimestamp()).thenReturn(1700000000000L);

        AuditRevisionEntity rev = new AuditRevisionEntity();
        listener.newRevision(rev);

        listener.entityChanged(String.class, "String", "id", RevisionType.ADD, rev);

        assertThat(rev.getActivities()).isEmpty();
    }

    @Test
    void entityChanged_deduplicatesCreateDeletePair() {
        when(securityClaimsExtractor.getAuthor()).thenReturn("user");
        when(securityClaimsExtractor.getEmail()).thenReturn("user@test.com");
        when(transactionTimestampContext.getTimestamp()).thenReturn(1700000000000L);

        AuditRevisionEntity rev = new AuditRevisionEntity();
        rev.setId(1);
        listener.newRevision(rev);

        // First: ADD creates an activity
        listener.entityChanged(McpDeploymentEntity.class, "McpDeploymentEntity",
                "deploy-1", RevisionType.ADD, rev);
        assertThat(rev.getActivities()).hasSize(1);

        // Second: DEL on same resource removes the ADD and replaces with DEL
        listener.entityChanged(McpDeploymentEntity.class, "McpDeploymentEntity",
                "deploy-1", RevisionType.DEL, rev);

        assertThat(rev.getActivities()).hasSize(1);
        AuditActivityEntity activity = rev.getActivities().iterator().next();
        assertThat(activity.getActivityType()).isEqualTo(ActivityType.Delete);
    }

    @Test
    void entityChanged_skipsModAfterCreate() {
        when(securityClaimsExtractor.getAuthor()).thenReturn("user");
        when(securityClaimsExtractor.getEmail()).thenReturn("user@test.com");
        when(transactionTimestampContext.getTimestamp()).thenReturn(1700000000000L);

        AuditRevisionEntity rev = new AuditRevisionEntity();
        rev.setId(1);
        listener.newRevision(rev);

        // First: ADD
        listener.entityChanged(McpDeploymentEntity.class, "McpDeploymentEntity",
                "deploy-1", RevisionType.ADD, rev);

        // Second: MOD on same resource — should be skipped (returns early)
        listener.entityChanged(McpDeploymentEntity.class, "McpDeploymentEntity",
                "deploy-1", RevisionType.MOD, rev);

        assertThat(rev.getActivities()).hasSize(1);
        AuditActivityEntity activity = rev.getActivities().iterator().next();
        assertThat(activity.getActivityType()).isEqualTo(ActivityType.Create);
    }

    @Test
    void entityChanged_handlesNullEntityId() {
        when(securityClaimsExtractor.getAuthor()).thenReturn("user");
        when(securityClaimsExtractor.getEmail()).thenReturn("user@test.com");
        when(transactionTimestampContext.getTimestamp()).thenReturn(1700000000000L);

        AuditRevisionEntity rev = new AuditRevisionEntity();
        rev.setId(1);
        listener.newRevision(rev);

        listener.entityChanged(McpDeploymentEntity.class, "McpDeploymentEntity",
                null, RevisionType.ADD, rev);

        assertThat(rev.getActivities()).hasSize(1);
        AuditActivityEntity activity = rev.getActivities().iterator().next();
        assertThat(activity.getResourceId()).isNull();
    }
}
