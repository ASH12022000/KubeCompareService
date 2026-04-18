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
    private String environmentId;
    private String timestamp;
    private Map<String, List<Object>> resourceSpecs; // Stores category -> list of resource YAML/JSON
}
