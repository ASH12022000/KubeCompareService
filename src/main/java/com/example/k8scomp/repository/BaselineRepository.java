package com.example.k8scomp.repository;

import com.example.k8scomp.model.BaselineSnapshot;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.Optional;

public interface BaselineRepository extends MongoRepository<BaselineSnapshot, String> {
    Optional<BaselineSnapshot> findTopByEnvironmentIdOrderByTimestampDesc(String environmentId);
}
