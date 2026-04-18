package com.example.k8scomp.model;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import java.util.Map;
import java.util.List;

@Data
@Document(collection = "baseline_snapshots")
public class BaselineSnapshot {
    @Id
    private String id;
    private String userId;
    private String name;           // e.g. "Dev Cluster Baseline"
    private String environmentId;  // logical env identifier (jumpHost:namespace)
    private String namespace;
    private String clusterUrl;
    private String jumpHost;
    private String jumpUser;
    private String timestamp;
    private Map<String, List<Object>> resourceSpecs;
}
