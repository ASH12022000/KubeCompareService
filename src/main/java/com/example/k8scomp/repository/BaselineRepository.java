package com.example.k8scomp.repository;

import com.example.k8scomp.model.BaselineSnapshot;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.List;
import java.util.Optional;

public interface BaselineRepository extends MongoRepository<BaselineSnapshot, String> {
    List<BaselineSnapshot> findByUserId(String userId);
    Optional<BaselineSnapshot> findTopByEnvironmentIdOrderByTimestampDesc(String environmentId);
    Optional<BaselineSnapshot> findByUserIdAndEnvironmentId(String userId, String environmentId);
}
