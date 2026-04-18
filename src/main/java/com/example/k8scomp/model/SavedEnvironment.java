package com.example.k8scomp.model;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Document(collection = "environments")
public class SavedEnvironment {
    @Id
    private String id;
    private String userId;
    private String name;
    private String type; // JUMP or DIRECT
    private String clusterUrl;
    private String encryptedToken;
    private String jumpHost;
    private String jumpUser;
    private String encryptedJumpPassword;
}
