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
    private List<CategoryResult> results;
}
