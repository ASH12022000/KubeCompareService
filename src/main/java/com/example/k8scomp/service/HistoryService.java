package com.example.k8scomp.service;

import com.example.k8scomp.model.AuditLog;
import com.example.k8scomp.model.CategoryResult;
import com.example.k8scomp.model.ComparisonHistory;
import com.example.k8scomp.repository.AuditLogRepository;
import com.example.k8scomp.repository.HistoryRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class HistoryService {

    private final HistoryRepository historyRepository;
    private final AuditLogRepository auditLogRepository;

    public HistoryService(HistoryRepository historyRepository, AuditLogRepository auditLogRepository) {
        this.historyRepository = historyRepository;
        this.auditLogRepository = auditLogRepository;
    }

    /**
     * Saves a comparison result with full context so the UI can display
     * cluster URLs, namespaces, and status without extra lookups.
     *
     * @param userId              the authenticated user ID
     * @param results             the comparison result map (category → list of diffs)
     * @param primaryClusterUrl   URL / jump host of the first cluster
     * @param comparisonClusterUrl URL / jump host of the second cluster
     * @param primaryNamespace    namespace of the first cluster
     * @param comparisonNamespace namespace of the second cluster
     */
    public void saveHistory(String userId, Map<String, Object> results,
                            String primaryClusterUrl, String comparisonClusterUrl,
                            String primaryNamespace, String comparisonNamespace) {

        ComparisonHistory history = new ComparisonHistory();
        history.setUserId(userId);
        history.setTimestamp(LocalDateTime.now().toString());
        history.setPrimaryClusterUrl(primaryClusterUrl);
        history.setComparisonClusterUrl(comparisonClusterUrl);
        history.setPrimaryNamespace(primaryNamespace);
        history.setComparisonNamespace(comparisonNamespace);
        history.setStatus("SUCCESS");

        // Convert result map to CategoryResult list
        List<CategoryResult> categoryResults = results.entrySet().stream()
                .map(entry -> {
                    CategoryResult cr = new CategoryResult();
                    cr.setCategory(entry.getKey());
                    cr.setDetails(entry.getValue());
                    try {
                        cr.setMatch(((List<?>) entry.getValue()).stream()
                                .noneMatch(i -> !"MATCH".equals(((Map<?, ?>) i).get("status"))));
                    } catch (Exception e) {
                        cr.setMatch(false);
                    }
                    return cr;
                }).collect(Collectors.toList());

        history.setResults(categoryResults);
        historyRepository.save(history);

        // Pruning: keep only the last 10 records per user
        List<ComparisonHistory> latestHistory =
                historyRepository.findTop10ByUserIdOrderByTimestampDesc(userId);
        if (latestHistory.size() >= 10) {
            List<ComparisonHistory> allHistory = historyRepository.findAll().stream()
                    .filter(h -> h.getUserId().equals(userId))
                    .filter(h -> latestHistory.stream().noneMatch(l -> l.getId().equals(h.getId())))
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
