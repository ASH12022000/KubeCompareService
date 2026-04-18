package com.example.k8scomp.service;

import com.example.k8scomp.model.AuditLog;
import com.example.k8scomp.model.ComparisonHistory;
import com.example.k8scomp.repository.AuditLogRepository;
import com.example.k8scomp.repository.HistoryRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class HistoryService {

    private final HistoryRepository historyRepository;
    private final AuditLogRepository auditLogRepository;

    public HistoryService(HistoryRepository historyRepository, AuditLogRepository auditLogRepository) {
        this.historyRepository = historyRepository;
        this.auditLogRepository = auditLogRepository;
    }

    public void saveHistory(ComparisonHistory history) {
        historyRepository.save(history);
        
        // Pruning logic: Only keep the last 10 records per user
        List<ComparisonHistory> latestHistory = historyRepository.findTop10ByUserIdOrderByTimestampDesc(history.getUserId());
        if (latestHistory.size() >= 10) {
            List<ComparisonHistory> allHistory = historyRepository.findAll().stream()
                .filter(h -> h.getUserId().equals(history.getUserId()))
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
