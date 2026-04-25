package com.example.k8scomp.service;

import com.example.k8scomp.model.BaselineSnapshot;
import com.example.k8scomp.model.SavedEnvironment;
import com.example.k8scomp.repository.BaselineRepository;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

@Service
public class BaselineService {

    private final BaselineRepository baselineRepository;
    private final K8sClientFactory clientFactory;
    private final ComparisonService comparisonService;

    public BaselineService(BaselineRepository baselineRepository,
            K8sClientFactory clientFactory,
            ComparisonService comparisonService) {
        this.baselineRepository = baselineRepository;
        this.clientFactory = clientFactory;
        this.comparisonService = comparisonService;
    }

    /**
     * Captures live resources from a cluster and persists them as a baseline
     * snapshot.
     * Connection details are passed directly (no pre-saved environment lookup
     * required).
     */
    public BaselineSnapshot captureAndSave(String environmentId, String ns, List<String> checks,
            String jumpHost, String jumpUser, String jumpPassword,
            String clusterUrl, String token, String kubeconfig) throws Exception {

        try (KubernetesClient client = clientFactory.createClient(
                org.springframework.util.StringUtils.hasText(kubeconfig) ? "KUBECONFIG" : (!org.springframework.util.StringUtils.hasText(jumpHost) ? "DIRECT" : "JUMP"),
                clusterUrl, token, jumpHost, jumpUser, jumpPassword, kubeconfig)) {

            Map<String, List<Object>> specs = new HashMap<>();

            if (checks.contains("DEPLOYMENTS")) {
                var items = client.apps().deployments().inNamespace(ns).list().getItems();
                items.forEach(comparisonService::sanitizeResource);
                specs.put("deployments", (List<Object>) (List<?>) items);
            }
            if (checks.contains("CONFIGMAPS")) {
                var items = client.configMaps().inNamespace(ns).list().getItems();
                items.forEach(comparisonService::sanitizeResource);
                specs.put("configmaps", (List<Object>) (List<?>) items);
            }
            if (checks.contains("SERVICES")) {
                var items = client.services().inNamespace(ns).list().getItems();
                items.forEach(comparisonService::sanitizeResource);
                specs.put("services", (List<Object>) (List<?>) items);
            }
            if (checks.contains("PVC")) {
                var items = client.persistentVolumeClaims().inNamespace(ns).list().getItems();
                items.forEach(comparisonService::sanitizeResource);
                specs.put("pvcs", (List<Object>) (List<?>) items);
            }
            if (checks.contains("IMAGES")) {
                // Extract container images from deployments
                List<Object> images = new ArrayList<>();
                client.apps().deployments().inNamespace(ns).list().getItems()
                        .forEach(d -> d.getSpec().getTemplate().getSpec().getContainers().forEach(c -> images
                                .add(Map.of("deployment", d.getMetadata().getName(), "image", c.getImage()))));
                specs.put("images", images);
            }
            if (checks.contains("VIRTUALSERVICES")) {
                var items = client.resources(io.fabric8.istio.api.networking.v1alpha3.VirtualService.class)
                        .inNamespace(ns).list().getItems();
                items.forEach(comparisonService::sanitizeResource);
                specs.put("virtualservices", (List<Object>) (List<?>) items);
            }
            if (checks.contains("AUTH_POLICY")) {
                var items = client.resources(io.fabric8.istio.api.security.v1beta1.AuthorizationPolicy.class)
                        .inNamespace(ns).list().getItems();
                items.forEach(comparisonService::sanitizeResource);
                specs.put("authpolicies", (List<Object>) (List<?>) items);
            }

            BaselineSnapshot snapshot = new BaselineSnapshot();
            snapshot.setEnvironmentId(environmentId);
            snapshot.setTimestamp(LocalDateTime.now().toString());
            snapshot.setResourceSpecs(specs);
            // Populated by the controller with userId, name, etc. before final save
            return snapshot;
        }
    }

    /**
     * Compares the current live state of a cluster against a stored baseline snapshot.
     * Uses connection details already persisted on the snapshot itself — no extra input needed.
     */
    public Map<String, Object> compareLiveWithBaseline(String snapshotId) throws Exception {
        BaselineSnapshot snapshot = baselineRepository.findById(snapshotId)
                .orElseThrow(() -> new RuntimeException("Baseline snapshot not found: " + snapshotId));

        String jumpHost    = snapshot.getJumpHost()     != null ? snapshot.getJumpHost()     : "";
        String jumpUser    = snapshot.getJumpUser()     != null ? snapshot.getJumpUser()     : "";
        String clusterUrl  = snapshot.getClusterUrl()   != null ? snapshot.getClusterUrl()   : "";
        String token       = snapshot.getToken()        != null ? snapshot.getToken()        : "";
        String jumpPassword = snapshot.getJumpPassword() != null ? snapshot.getJumpPassword() : "";
        String ns          = snapshot.getNamespace()    != null ? snapshot.getNamespace()    : "default";
        String kubeconfig  = snapshot.getKubeconfig()   != null ? snapshot.getKubeconfig()   : "";
        Map<String, List<Object>> baselineSpecs = snapshot.getResourceSpecs();

        try (KubernetesClient liveClient = clientFactory.createClient(
                org.springframework.util.StringUtils.hasText(kubeconfig) ? "KUBECONFIG" : (!org.springframework.util.StringUtils.hasText(jumpHost) ? "DIRECT" : "JUMP"),
                clusterUrl, token, jumpHost, jumpUser, jumpPassword, kubeconfig)) {
            Map<String, Object> diffResults = new HashMap<>();

            if (baselineSpecs.containsKey("deployments")) {
                var live = liveClient.apps().deployments().inNamespace(ns).list().getItems();
                diffResults.put("deployments", comparisonService.compareLiveWithSpecs(live, baselineSpecs.get("deployments")));
            }
            if (baselineSpecs.containsKey("configmaps")) {
                var live = liveClient.configMaps().inNamespace(ns).list().getItems();
                diffResults.put("configmaps", comparisonService.compareLiveWithSpecs(live, baselineSpecs.get("configmaps")));
            }
            if (baselineSpecs.containsKey("services")) {
                var live = liveClient.services().inNamespace(ns).list().getItems();
                diffResults.put("services", comparisonService.compareLiveWithSpecs(live, baselineSpecs.get("services")));
            }
            if (baselineSpecs.containsKey("pvcs")) {
                var live = liveClient.persistentVolumeClaims().inNamespace(ns).list().getItems();
                diffResults.put("pvcs", comparisonService.compareLiveWithSpecs(live, baselineSpecs.get("pvcs")));
            }
            if (baselineSpecs.containsKey("images")) {
                // Re-extract live images as plain maps
                List<Map<String, String>> liveImages = new ArrayList<>();
                liveClient.apps().deployments().inNamespace(ns).list().getItems().forEach(d ->
                    d.getSpec().getTemplate().getSpec().getContainers().forEach(c ->
                        liveImages.add(Map.of("deployment", d.getMetadata().getName(), "image", c.getImage()))
                    )
                );

                // Compare image maps directly (not HasMetadata objects)
                List<Map<String, Object>> imageDiffs = new ArrayList<>();
                List<Object> baselineImages = baselineSpecs.get("images");
                for (Map<String, String> live : liveImages) {
                    boolean found = baselineImages.stream().anyMatch(b ->
                        b instanceof Map && live.get("deployment").equals(((Map<?,?>) b).get("deployment"))
                            && live.get("image").equals(((Map<?,?>) b).get("image")));
                    if (!found) {
                        imageDiffs.add(Map.of("name", live.get("deployment"), "status", "MISMATCH",
                            "liveImage", live.get("image")));
                    }
                }
                diffResults.put("images", imageDiffs);
            }
            if (baselineSpecs.containsKey("virtualservices")) {
                var live = liveClient.resources(io.fabric8.istio.api.networking.v1alpha3.VirtualService.class)
                    .inNamespace(ns).list().getItems();
                diffResults.put("virtualservices", comparisonService.compareLiveWithSpecs(live, baselineSpecs.get("virtualservices")));
            }
            if (baselineSpecs.containsKey("authpolicies")) {
                var live = liveClient.resources(io.fabric8.istio.api.security.v1beta1.AuthorizationPolicy.class)
                    .inNamespace(ns).list().getItems();
                diffResults.put("authpolicies", comparisonService.compareLiveWithSpecs(live, baselineSpecs.get("authpolicies")));
            }

            return diffResults;
        }
    }
}
