package com.example.k8scomp.model;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Document(collection = "users")
public class User {
    @Id
    private String id;
    private String email;
    private String password;
    private boolean verified;
    private String otp;
    private java.time.LocalDateTime createdAt;
}
