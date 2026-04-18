package com.example.k8scomp.model;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Document(collection = "audit_logs")
public class AuditLog {
    @Id
    private String id;
    private String userId;
    private String action;
    private String timestamp;
    private String details;
}
