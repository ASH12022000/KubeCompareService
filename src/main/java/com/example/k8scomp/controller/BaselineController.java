package com.example.k8scomp.controller;

import com.example.k8scomp.model.BaselineSnapshot;
import com.example.k8scomp.repository.BaselineRepository;
import com.example.k8scomp.service.BaselineService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.time.Instant;
import java.util.*;

@RestController
@RequestMapping("/api/baselines")
public class BaselineController {

    private static final Logger log = LoggerFactory.getLogger(BaselineController.class);

    private final BaselineService baselineService;
    private final BaselineRepository baselineRepository;

    public BaselineController(BaselineService baselineService, BaselineRepository baselineRepository) {
        this.baselineService = baselineService;
        this.baselineRepository = baselineRepository;
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<List<BaselineSnapshot>> getUserBaselines(@PathVariable String userId) {
        log.info("Fetching baselines for userId={}", userId);
        var baselines = baselineRepository.findByUserId(userId);
        log.debug("Returning {} baselines for userId={}", baselines.size(), userId);
        return ResponseEntity.ok(baselines);
    }

    @GetMapping("/check")
    public ResponseEntity<?> checkExists(@RequestParam String userId, @RequestParam String environmentId) {
        log.info("Checking baseline existence: userId={}, envId={}", userId, environmentId);
        Optional<BaselineSnapshot> existing = baselineRepository.findByUserIdAndEnvironmentId(userId, environmentId);
        if (existing.isPresent()) {
            log.info("Baseline EXISTS: id={}, name={}", existing.get().getId(), existing.get().getName());
            return ResponseEntity.ok(Map.of("exists", true, "id", existing.get().getId(), "name", existing.get().getName()));
        }
        log.info("No existing baseline found for envId={}", environmentId);
        return ResponseEntity.ok(Map.of("exists", false));
    }

    @PostMapping("/save")
    public ResponseEntity<?> saveBaseline(@RequestBody Map<String, Object> body) {
        String userId = (String) body.getOrDefault("userId", "anonymous");
        String name   = (String) body.getOrDefault("name", "My Baseline");
        log.info("Save baseline request: userId={}, name={}", userId, name);
        try {
            String namespace    = (String) body.getOrDefault("namespace", "default");
            String clusterUrl   = (String) body.getOrDefault("clusterUrl", "");
            String jumpHost     = (String) body.getOrDefault("jumpHost", "");
            String jumpUser     = (String) body.getOrDefault("jumpUser", "");
            String jumpPassword = (String) body.getOrDefault("jumpPassword", "");
            boolean override    = Boolean.TRUE.equals(body.get("override"));
            @SuppressWarnings("unchecked")
            List<String> checks = (List<String>) body.getOrDefault("checks", List.of("DEPLOYMENTS", "CONFIGMAPS"));

            String environmentId = jumpHost.isBlank() ? (clusterUrl + ":" + namespace) : (jumpHost + ":" + namespace);
            log.debug("Computed environmentId={}, override={}, checks={}", environmentId, override, checks);

            Optional<BaselineSnapshot> existing = baselineRepository.findByUserIdAndEnvironmentId(userId, environmentId);
            if (existing.isPresent() && !override) {
                log.warn("Baseline CONFLICT: existing id={}, name={}", existing.get().getId(), existing.get().getName());
                return ResponseEntity.status(409).body(Map.of(
                    "conflict", true,
                    "message", "A baseline already exists for this environment.",
                    "existingId", existing.get().getId(),
                    "existingName", existing.get().getName()
                ));
            }
            if (existing.isPresent()) {
                log.info("Overriding existing baseline id={}", existing.get().getId());
                baselineRepository.deleteById(existing.get().getId());
            }

            log.info("Capturing live cluster snapshot for envId={}, ns={}", environmentId, namespace);
            BaselineSnapshot snapshot = baselineService.captureAndSave(
                environmentId, namespace, checks,
                jumpHost, jumpUser, jumpPassword,
                clusterUrl, ""
            );
            snapshot.setUserId(userId);
            snapshot.setName(name);
            snapshot.setNamespace(namespace);
            snapshot.setClusterUrl(clusterUrl);
            snapshot.setJumpHost(jumpHost);
            snapshot.setJumpUser(jumpUser);
            snapshot.setEnvironmentId(environmentId);
            snapshot.setTimestamp(Instant.now().toString());
            baselineRepository.save(snapshot);

            log.info("Baseline saved: id={}, name={}, envId={}", snapshot.getId(), name, environmentId);
            return ResponseEntity.ok(snapshot);
        } catch (Exception e) {
            log.error("Baseline save FAILED for userId={}, name={}: {}", userId, name, e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    @DeleteMapping("/{userId}/{id}")
    public ResponseEntity<?> deleteBaseline(@PathVariable String userId, @PathVariable String id) {
        log.info("Delete baseline request: userId={}, baselineId={}", userId, id);
        return baselineRepository.findById(id).map(snapshot -> {
            if (!snapshot.getUserId().equals(userId)) {
                log.warn("Delete FORBIDDEN: userId={} tried to delete baseline owned by {}", userId, snapshot.getUserId());
                return ResponseEntity.status(403).body("Forbidden");
            }
            baselineRepository.deleteById(id);
            log.info("Baseline deleted: id={}, name={}", id, snapshot.getName());
            return ResponseEntity.ok().build();
        }).orElseGet(() -> {
            log.warn("Delete failed — baseline id={} not found", id);
            return ResponseEntity.notFound().build();
        });
    }

    @PostMapping("/compare/{snapshotId}")
    public ResponseEntity<?> compareWithBaseline(@PathVariable String snapshotId) {
        log.info("Baseline comparison started: snapshotId={}", snapshotId);
        try {
            Map<String, Object> results = baselineService.compareLiveWithBaseline(snapshotId);
            log.info("Baseline comparison COMPLETED: snapshotId={}, categories={}", snapshotId, results.keySet());
            return ResponseEntity.ok(results);
        } catch (Exception e) {
            log.error("Baseline comparison FAILED for snapshotId={}: {}", snapshotId, e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }
}
