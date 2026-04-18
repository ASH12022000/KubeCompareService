package com.example.k8scomp.repository;

import com.example.k8scomp.model.AuditLog;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface AuditLogRepository extends MongoRepository<AuditLog, String> {
}
