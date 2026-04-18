package com.example.k8scomp.model;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import java.util.List;

@Data
@Document(collection = "comparison_history")
public class ComparisonHistory {
    @Id
    private String id;
    private String userId;
    private String timestamp;

    // Context fields (populated from the request, displayed in UI)
    private String primaryClusterUrl;
    private String comparisonClusterUrl;
    private String primaryNamespace;
    private String comparisonNamespace;
    private String status; // "SUCCESS" | "ERROR"

    private List<CategoryResult> results;
}
