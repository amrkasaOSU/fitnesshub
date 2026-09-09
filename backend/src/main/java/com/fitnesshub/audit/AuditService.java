package com.fitnesshub.audit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);

    private final AuditLogRepository auditLogRepository;

    public AuditService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    /**
     * Runs in its own transaction so an audit-log failure never rolls back the
     * business operation it is describing, and a rollback of the business
     * operation never erases the audit trail of the attempt.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(UUID actorUserId, AuditAction action, String targetType, UUID targetId, String metadata) {
        try {
            auditLogRepository.save(new AuditLog(actorUserId, action, targetType, targetId, metadata));
        } catch (Exception e) {
            log.warn("Failed to write audit log for action {}", action, e);
        }
    }

    public void record(UUID actorUserId, AuditAction action) {
        record(actorUserId, action, null, null, null);
    }

    public void record(UUID actorUserId, AuditAction action, String targetType, UUID targetId) {
        record(actorUserId, action, targetType, targetId, null);
    }
}
