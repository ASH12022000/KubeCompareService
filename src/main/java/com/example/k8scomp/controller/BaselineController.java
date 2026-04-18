package com.example.k8scomp.controller;

import com.example.k8scomp.model.BaselineSnapshot;
import com.example.k8scomp.service.BaselineService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api/baselines")
public class BaselineController {

    private final BaselineService baselineService;

    public BaselineController(BaselineService baselineService) {
        this.baselineService = baselineService;
    }

    @PostMapping("/save/{envId}")
    public ResponseEntity<?> saveBaseline(@PathVariable String envId, @RequestBody Map<String, Object> options) {
        try {
            String ns = (String) options.getOrDefault("namespace", "default");
            List<String> checks = (List<String>) options.getOrDefault("checks", List.of("DEPLOYMENTS", "CONFIGMAPS"));
            
            BaselineSnapshot snapshot = baselineService.captureAndSave(envId, ns, checks);
            return ResponseEntity.ok(snapshot);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/compare/{envId}/{snapshotId}")
    public ResponseEntity<?> compareWithBaseline(@PathVariable String envId, @PathVariable String snapshotId, 
                                                @RequestBody Map<String, Object> options) {
        try {
            String ns = (String) options.getOrDefault("namespace", "default");
            List<String> checks = (List<String>) options.getOrDefault("checks", List.of("DEPLOYMENTS", "CONFIGMAPS"));
            
            Map<String, Object> results = baselineService.compareLiveWithBaseline(envId, snapshotId, ns, checks);
            return ResponseEntity.ok(results);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }
}
