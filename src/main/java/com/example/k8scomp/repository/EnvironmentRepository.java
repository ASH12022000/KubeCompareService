package com.example.k8scomp.repository;

import com.example.k8scomp.model.SavedEnvironment;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.List;

public interface EnvironmentRepository extends MongoRepository<SavedEnvironment, String> {
    List<SavedEnvironment> findByUserId(String userId);
}
