package com.example.insurancecrm.repository;

import com.example.insurancecrm.domain.AuditLog;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface AuditLogRepository extends MongoRepository<AuditLog, String> {

    List<AuditLog> findByEntityTypeAndEntityIdOrderByTimestampDesc(String entityType, String entityId);

    List<AuditLog> findByPerformedByOrderByTimestampDesc(String performedBy);
}
