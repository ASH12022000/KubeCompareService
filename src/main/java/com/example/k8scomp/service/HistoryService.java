package com.example.k8scomp.service;

import com.example.k8scomp.model.AuditLog;
import com.example.k8scomp.model.ComparisonHistory;
import com.example.k8scomp.repository.AuditLogRepository;
import com.example.k8scomp.repository.HistoryRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
public class HistoryService {

    private final HistoryRepository historyRepository;
    private final AuditLogRepository auditLogRepository;

    public HistoryService(HistoryRepository historyRepository, AuditLogRepository auditLogRepository) {
        this.historyRepository = historyRepository;
        this.auditLogRepository = auditLogRepository;
    }

    public void saveHistory(String userId, Map<String, Object> results) {
        ComparisonHistory history = new ComparisonHistory();
        history.setUserId(userId);
        history.setTimestamp(LocalDateTime.now().toString());

        // Convert Map to CategoryResult list
        List<com.example.k8scomp.model.CategoryResult> categoryResults = results.entrySet().stream()
                .map(entry -> {
                    com.example.k8scomp.model.CategoryResult cr = new com.example.k8scomp.model.CategoryResult();
                    cr.setCategory(entry.getKey());
                    cr.setDetails(entry.getValue());
                    // Basic match detection for summary
                    cr.setMatch(((List<?>) entry.getValue()).stream()
                            .noneMatch(i -> !((Map<?, ?>) i).get("status").equals("MATCH")));
                    return cr;
                }).collect(java.util.stream.Collectors.toList());

        history.setResults(categoryResults);
        historyRepository.save(history);

        // Pruning logic: Only keep the last 10 records per user
        List<ComparisonHistory> latestHistory = historyRepository.findTop10ByUserIdOrderByTimestampDesc(userId);
        if (latestHistory.size() >= 10) {
            List<ComparisonHistory> allHistory = historyRepository.findAll().stream()
                    .filter(h -> h.getUserId().equals(userId))
                    .filter(h -> !latestHistory.stream().anyMatch(l -> l.getId().equals(h.getId())))
                    .toList();
            historyRepository.deleteAll(allHistory);
        }
    }

    public void logAudit(String userId, String action, String details) {
        AuditLog log = new AuditLog();
        log.setUserId(userId);
        log.setAction(action);
        log.setDetails(details);
        log.setTimestamp(LocalDateTime.now().toString());
        auditLogRepository.save(log);
    }
}
