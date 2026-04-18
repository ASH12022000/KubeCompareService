package com.example.k8scomp.repository;

import com.example.k8scomp.model.ComparisonHistory;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.List;

public interface HistoryRepository extends MongoRepository<ComparisonHistory, String> {
    List<ComparisonHistory> findTop10ByUserIdOrderByTimestampDesc(String userId);
}
