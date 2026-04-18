package com.example.k8scomp.controller;

import com.example.k8scomp.model.BaselineSnapshot;
import com.example.k8scomp.repository.BaselineRepository;
import com.example.k8scomp.service.BaselineService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.time.Instant;
import java.util.*;

@RestController
@RequestMapping("/api/baselines")
public class BaselineController {

    private final BaselineService baselineService;
    private final BaselineRepository baselineRepository;

    public BaselineController(BaselineService baselineService, BaselineRepository baselineRepository) {
        this.baselineService = baselineService;
        this.baselineRepository = baselineRepository;
    }

    /** List all baselines for a user */
    @GetMapping("/user/{userId}")
    public ResponseEntity<List<BaselineSnapshot>> getUserBaselines(@PathVariable String userId) {
        return ResponseEntity.ok(baselineRepository.findByUserId(userId));
    }

    /**
     * Check if a baseline already exists for this user+environment.
     * Returns 200 with { exists: true/false, id: "..." }
     */
    @GetMapping("/check")
    public ResponseEntity<?> checkExists(@RequestParam String userId, @RequestParam String environmentId) {
        Optional<BaselineSnapshot> existing = baselineRepository.findByUserIdAndEnvironmentId(userId, environmentId);
        if (existing.isPresent()) {
            return ResponseEntity.ok(Map.of("exists", true, "id", existing.get().getId(), "name", existing.get().getName()));
        }
        return ResponseEntity.ok(Map.of("exists", false));
    }

    /**
     * Save or override a baseline. Accepts full cluster connection details + name.
     * Body: { userId, name, namespace, clusterUrl, jumpHost, jumpUser, jumpPassword, checks[], override }
     */
    @PostMapping("/save")
    public ResponseEntity<?> saveBaseline(@RequestBody Map<String, Object> body) {
        try {
            String userId      = (String) body.getOrDefault("userId", "anonymous");
            String name        = (String) body.getOrDefault("name", "My Baseline");
            String namespace   = (String) body.getOrDefault("namespace", "default");
            String clusterUrl  = (String) body.getOrDefault("clusterUrl", "");
            String jumpHost    = (String) body.getOrDefault("jumpHost", "");
            String jumpUser    = (String) body.getOrDefault("jumpUser", "");
            String jumpPassword = (String) body.getOrDefault("jumpPassword", "");
            boolean override   = Boolean.TRUE.equals(body.get("override"));
            @SuppressWarnings("unchecked")
            List<String> checks = (List<String>) body.getOrDefault("checks", List.of("DEPLOYMENTS", "CONFIGMAPS"));

            // Build a deterministic environmentId from host+namespace
            String environmentId = jumpHost.isBlank() ? (clusterUrl + ":" + namespace) : (jumpHost + ":" + namespace);

            // Duplicate check
            Optional<BaselineSnapshot> existing = baselineRepository.findByUserIdAndEnvironmentId(userId, environmentId);
            if (existing.isPresent() && !override) {
                return ResponseEntity.status(409).body(Map.of(
                    "conflict", true,
                    "message", "A baseline already exists for this environment.",
                    "existingId", existing.get().getId(),
                    "existingName", existing.get().getName()
                ));
            }
            // If override, delete the old one first
            existing.ifPresent(s -> baselineRepository.deleteById(s.getId()));

            // Capture snapshot via BaselineService passing credentials directly
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

            return ResponseEntity.ok(snapshot);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    /** Delete a baseline by id (user must own it) */
    @DeleteMapping("/{userId}/{id}")
    public ResponseEntity<?> deleteBaseline(@PathVariable String userId, @PathVariable String id) {
        return baselineRepository.findById(id).map(snapshot -> {
            if (!snapshot.getUserId().equals(userId)) {
                return ResponseEntity.status(403).body("Forbidden");
            }
            baselineRepository.deleteById(id);
            return ResponseEntity.ok().build();
        }).orElse(ResponseEntity.notFound().build());
    }

    /** Compare a saved baseline with live cluster — uses stored connection details */
    @PostMapping("/compare/{snapshotId}")
    public ResponseEntity<?> compareWithBaseline(@PathVariable String snapshotId) {
        try {
            Map<String, Object> results = baselineService.compareLiveWithBaseline(snapshotId);
            return ResponseEntity.ok(results);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }
}
