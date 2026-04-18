package com.example.k8scomp.controller;

import com.example.k8scomp.model.SavedEnvironment;
import com.example.k8scomp.service.ComparisonService;
import com.example.k8scomp.service.K8sClientFactory;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.*;
import java.util.List;

@RestController
@RequestMapping("/api/comparison")
public class ComparisonController {

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
        return historyRepository.findById(historyId)
            .map(history -> {
                // Convert list of CategoryResult back to Map for ExportService
                Map<String, Object> resultsMap = new HashMap<>();
                history.getResults().forEach(r -> resultsMap.put(r.getCategory(), r.getDetails()));
                
                byte[] pdf = exportService.generatePdfReport(resultsMap);
                return ResponseEntity.ok()
                    .header("Content-Disposition", "attachment; filename=report.pdf")
                    .contentType(org.springframework.http.MediaType.APPLICATION_PDF)
                    .body(pdf);
            }).orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/connect")
    public ResponseEntity<?> testConnection(@RequestBody SavedEnvironment env) {
        try {
            KubernetesClient client = clientFactory.createClient(env.getType(), env.getClusterUrl(), 
                                                                env.getEncryptedToken(), env.getJumpHost(), 
                                                                env.getJumpUser(), env.getEncryptedJumpPassword());
            client.namespaces().list();
            return ResponseEntity.ok(Map.of("status", "SUCCESS"));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("status", "ERROR", "message", e.getMessage()));
        }
    }

    @PostMapping("/run")
    public ResponseEntity<?> runComparison(@RequestBody ComparisonRequest request) {
        try {
            SavedEnvironment env1 = request.getEnv1();
            KubernetesClient c1 = clientFactory.createClient(env1.getType(), env1.getClusterUrl(), 
                                                           env1.getEncryptedToken(), env1.getJumpHost(), 
                                                           env1.getJumpUser(), env1.getEncryptedJumpPassword());
            
            SavedEnvironment env2 = request.getEnv2();
            KubernetesClient c2 = clientFactory.createClient(env2.getType(), env2.getClusterUrl(), 
                                                           env2.getEncryptedToken(), env2.getJumpHost(), 
                                                           env2.getJumpUser(), env2.getEncryptedJumpPassword());

            Map<String, Object> results = new HashMap<>();
            if (request.getChecks().contains("DEPLOYMENTS")) {
                results.put("deployments", comparisonService.compareDeployments(c1, c2, request.getNs1(), request.getNs2()));
            }
            if (request.getChecks().contains("CONFIGMAPS")) {
                results.put("configmaps", comparisonService.compareConfigMaps(c1, c2, request.getNs1(), request.getNs2()));
            }
            if (request.getChecks().contains("SERVICES")) {
                results.put("services", comparisonService.compareServices(c1, c2, request.getNs1(), request.getNs2()));
            }
            if (request.getChecks().contains("PVC")) {
                results.put("pvcs", comparisonService.comparePVCs(c1, c2, request.getNs1(), request.getNs2()));
            }
            if (request.getChecks().contains("IMAGES")) {
                results.put("images", comparisonService.compareImages(c1, c2, request.getNs1(), request.getNs2()));
            }
            if (request.getChecks().contains("VIRTUALSERVICES") || request.getChecks().contains("AUTH_POLICY")) {
                results.put("istio", comparisonService.compareIstio(c1, c2, request.getNs1(), request.getNs2()));
            }
            
            // Save full results to History with real userId and display context
            String userId = (request.getUserId() != null && !request.getUserId().isBlank())
                ? request.getUserId() : "anonymous";

            String primaryUrl    = env1.getJumpHost() != null && !env1.getJumpHost().isBlank()
                ? "jump://" + env1.getJumpHost() : env1.getClusterUrl();
            String comparisonUrl = env2.getJumpHost() != null && !env2.getJumpHost().isBlank()
                ? "jump://" + env2.getJumpHost() : env2.getClusterUrl();

            historyService.saveHistory(userId, results,
                primaryUrl, comparisonUrl,
                request.getNs1(), request.getNs2());

            // Log Audit
            historyService.logAudit(userId, "RUN_COMPARISON", "Compared " + request.getNs1() + " vs " + request.getNs2());
            
            return ResponseEntity.ok(results);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/history/{userId}")
    public ResponseEntity<List<com.example.k8scomp.model.ComparisonHistory>> getUserHistory(@PathVariable String userId) {
        return ResponseEntity.ok(historyRepository.findTop10ByUserIdOrderByTimestampDesc(userId));
    }

    @DeleteMapping("/history/{userId}/{id}")
    public ResponseEntity<?> deleteUserHistory(@PathVariable String userId, @PathVariable String id) {
        return historyRepository.findById(id)
            .map(record -> {
                if (!record.getUserId().equals(userId)) {
                    return ResponseEntity.status(403).body("Forbidden: record does not belong to this user");
                }
                historyRepository.deleteById(id);
                return ResponseEntity.ok().build();
            })
            .orElse(ResponseEntity.notFound().build());
    }
}
