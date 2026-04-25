package com.example.k8scomp.controller;

import com.example.k8scomp.model.SavedEnvironment;
import com.example.k8scomp.service.ComparisonService;
import com.example.k8scomp.service.K8sClientFactory;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.*;
import java.util.List;

@RestController
@RequestMapping("/api/comparison")
public class ComparisonController {

    private static final Logger log = LoggerFactory.getLogger(ComparisonController.class);

    private final K8sClientFactory clientFactory;
    private final ComparisonService comparisonService;
    private final com.example.k8scomp.service.HistoryService historyService;
    private final com.example.k8scomp.service.ExportService exportService;
    private final com.example.k8scomp.repository.HistoryRepository historyRepository;

    public ComparisonController(K8sClientFactory clientFactory, ComparisonService comparisonService,
                                com.example.k8scomp.service.HistoryService historyService,
                                com.example.k8scomp.repository.HistoryRepository historyRepository,
                                com.example.k8scomp.service.ExportService exportService) {
        this.clientFactory = clientFactory;
        this.comparisonService = comparisonService;
        this.historyService = historyService;
        this.historyRepository = historyRepository;
        this.exportService = exportService;
    }

    @GetMapping("/export/{historyId}")
    public ResponseEntity<byte[]> exportHistory(@PathVariable String historyId) {
        log.info("Export PDF requested for historyId={}", historyId);
        return historyRepository.findById(historyId)
            .map(history -> {
                Map<String, Object> resultsMap = new HashMap<>();
                history.getResults().forEach(r -> resultsMap.put(r.getCategory(), r.getDetails()));

                byte[] pdf = exportService.generatePdfReport(resultsMap);
                log.info("PDF generated for historyId={}, size={}bytes", historyId, pdf.length);
                return ResponseEntity.ok()
                    .header("Content-Disposition", "attachment; filename=report.pdf")
                    .contentType(org.springframework.http.MediaType.APPLICATION_PDF)
                    .body(pdf);
            }).orElseGet(() -> {
                log.warn("Export failed — historyId={} not found", historyId);
                return ResponseEntity.notFound().build();
            });
    }

    @PostMapping("/connect")
    public ResponseEntity<?> testConnection(@RequestBody SavedEnvironment env) {
        if (env == null) return ResponseEntity.badRequest().body(Map.of("status", "ERROR", "message", "Environment data missing"));
        log.info("Connection test: type={}, jumpHost={}, clusterUrl={}", env.getType(), env.getJumpHost(), env.getClusterUrl());
        try {
            KubernetesClient client = clientFactory.createClient(env.getType(), env.getClusterUrl(),
                                                                 env.getEncryptedToken(), env.getJumpHost(),
                                                                 env.getJumpUser(), env.getEncryptedJumpPassword(),
                                                                 env.getKubeconfig());
            client.namespaces().list();
            log.info("Connection test SUCCESS for {}", env.getJumpHost() != null ? env.getJumpHost() : env.getClusterUrl());
            return ResponseEntity.ok(Map.of("status", "SUCCESS"));
        } catch (Exception e) {
            log.error("Connection test FAILED: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of("status", "ERROR", "message", e.getMessage()));
        }
    }

    @PostMapping("/run")
    public ResponseEntity<?> runComparison(@RequestBody ComparisonRequest request) {
        log.info("Comparison run started: userId={}, ns1={}, ns2={}, checks={}",
            request.getUserId(), request.getNs1(), request.getNs2(), request.getChecks());
        try {
            SavedEnvironment env1 = request.getEnv1();
            if (env1 == null) throw new RuntimeException("Primary environment data (env1) missing");
            log.debug("Creating K8s client for cluster1: type={}, clusterUrl={}, jumpHost={}", env1.getType(), env1.getClusterUrl(), env1.getJumpHost());
            KubernetesClient c1 = clientFactory.createClient(env1.getType(), env1.getClusterUrl(),
                                                           env1.getEncryptedToken(), env1.getJumpHost(),
                                                           env1.getJumpUser(), env1.getEncryptedJumpPassword(),
                                                           env1.getKubeconfig());

            SavedEnvironment env2 = request.getEnv2();
            if (env2 == null) throw new RuntimeException("Comparison environment data (env2) missing");
            log.debug("Creating K8s client for cluster2: type={}, clusterUrl={}, jumpHost={}", env2.getType(), env2.getClusterUrl(), env2.getJumpHost());
            KubernetesClient c2 = clientFactory.createClient(env2.getType(), env2.getClusterUrl(),
                                                           env2.getEncryptedToken(), env2.getJumpHost(),
                                                           env2.getJumpUser(), env2.getEncryptedJumpPassword(),
                                                           env2.getKubeconfig());

            Map<String, Object> results = new HashMap<>();
            if (request.getChecks().contains("DEPLOYMENTS")) {
                log.debug("Comparing DEPLOYMENTS...");
                results.put("deployments", comparisonService.compareDeployments(c1, c2, request.getNs1(), request.getNs2()));
            }
            if (request.getChecks().contains("CONFIGMAPS")) {
                log.debug("Comparing CONFIGMAPS...");
                results.put("configmaps", comparisonService.compareConfigMaps(c1, c2, request.getNs1(), request.getNs2()));
            }
            if (request.getChecks().contains("SERVICES")) {
                log.debug("Comparing SERVICES...");
                results.put("services", comparisonService.compareServices(c1, c2, request.getNs1(), request.getNs2()));
            }
            if (request.getChecks().contains("PVC")) {
                log.debug("Comparing PVCs...");
                results.put("pvcs", comparisonService.comparePVCs(c1, c2, request.getNs1(), request.getNs2()));
            }
            if (request.getChecks().contains("IMAGES")) {
                log.debug("Comparing IMAGES...");
                results.put("images", comparisonService.compareImages(c1, c2, request.getNs1(), request.getNs2()));
            }
            if (request.getChecks().contains("VIRTUALSERVICES") || request.getChecks().contains("AUTH_POLICY")) {
                log.debug("Comparing ISTIO resources...");
                results.put("istio", comparisonService.compareIstio(c1, c2, request.getNs1(), request.getNs2()));
            }

            String userId = (request.getUserId() != null && !request.getUserId().isBlank())
                ? request.getUserId() : "anonymous";

            String primaryUrl    = env1.getJumpHost() != null && !env1.getJumpHost().isBlank()
                ? "jump://" + env1.getJumpHost() : env1.getClusterUrl();
            String comparisonUrl = env2.getJumpHost() != null && !env2.getJumpHost().isBlank()
                ? "jump://" + env2.getJumpHost() : env2.getClusterUrl();

            historyService.saveHistory(userId, results, primaryUrl, comparisonUrl,
                request.getNs1(), request.getNs2());
            historyService.logAudit(userId, "RUN_COMPARISON", "Compared " + request.getNs1() + " vs " + request.getNs2());

            log.info("Comparison run COMPLETED for userId={}, categories={}", userId, results.keySet());
            return ResponseEntity.ok(results);
        } catch (Exception e) {
            log.error("Comparison run FAILED: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/history/{userId}")
    public ResponseEntity<List<com.example.k8scomp.model.ComparisonHistory>> getUserHistory(@PathVariable String userId) {
        log.info("Fetching history for userId={}", userId);
        var history = historyRepository.findTop10ByUserIdOrderByTimestampDesc(userId);
        log.debug("Returning {} history records for userId={}", history.size(), userId);
        return ResponseEntity.ok(history);
    }

    @DeleteMapping("/history/{userId}/{id}")
    public ResponseEntity<?> deleteUserHistory(@PathVariable String userId, @PathVariable String id) {
        log.info("Delete history request: userId={}, recordId={}", userId, id);
        return historyRepository.findById(id)
            .map(record -> {
                if (!record.getUserId().equals(userId)) {
                    log.warn("Delete FORBIDDEN: userId={} attempted to delete record owned by {}", userId, record.getUserId());
                    return ResponseEntity.status(403).body("Forbidden: record does not belong to this user");
                }
                historyRepository.deleteById(id);
                log.info("History record deleted: id={}", id);
                return ResponseEntity.ok().build();
            })
            .orElseGet(() -> {
                log.warn("Delete failed — history record id={} not found", id);
                return ResponseEntity.notFound().build();
            });
    }
}
