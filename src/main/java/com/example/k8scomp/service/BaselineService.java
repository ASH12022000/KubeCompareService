package com.example.k8scomp.service;

import com.example.k8scomp.model.BaselineSnapshot;
import com.example.k8scomp.model.CategoryResult;
import com.example.k8scomp.model.SavedEnvironment;
import com.example.k8scomp.repository.BaselineRepository;
import com.example.k8scomp.repository.EnvironmentRepository;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class BaselineService {

    private final BaselineRepository baselineRepository;
    private final EnvironmentRepository environmentRepository;
    private final K8sClientFactory clientFactory;
    private final ComparisonService comparisonService;

    public BaselineService(BaselineRepository baselineRepository, EnvironmentRepository environmentRepository,
                           K8sClientFactory clientFactory, ComparisonService comparisonService) {
        this.baselineRepository = baselineRepository;
        this.environmentRepository = environmentRepository;
        this.clientFactory = clientFactory;
        this.comparisonService = comparisonService;
    }

    public BaselineSnapshot captureAndSave(String envId, String ns, List<String> checks) throws Exception {
        SavedEnvironment env = environmentRepository.findById(envId)
                .orElseThrow(() -> new RuntimeException("Environment not found"));

        try (KubernetesClient client = clientFactory.createClient(env.getType(), env.getClusterUrl(), 
                                                                env.getEncryptedToken(), env.getJumpHost(), 
                                                                env.getJumpUser(), env.getEncryptedJumpPassword())) {
            
            Map<String, List<Object>> specs = new HashMap<>();
            if (checks.contains("DEPLOYMENTS")) {
                specs.put("deployments", (List) client.apps().deployments().inNamespace(ns).list().getItems());
            }
            if (checks.contains("CONFIGMAPS")) {
                specs.put("configmaps", (List) client.configMaps().inNamespace(ns).list().getItems());
            }
            if (checks.contains("SERVICES")) {
                specs.put("services", (List) client.services().inNamespace(ns).list().getItems());
            }
            if (checks.contains("PVC")) {
                specs.put("pvcs", (List) client.persistentVolumeClaims().inNamespace(ns).list().getItems());
            }
            if (checks.contains("VIRTUALSERVICES")) {
                specs.put("virtualservices", (List) client.resources(io.fabric8.istio.api.networking.v1alpha3.VirtualService.class).inNamespace(ns).list().getItems());
            }
            if (checks.contains("AUTH_POLICY")) {
                specs.put("authpolicies", (List) client.resources(io.fabric8.istio.api.security.v1beta1.AuthorizationPolicy.class).inNamespace(ns).list().getItems());
            }
            
            BaselineSnapshot snapshot = new BaselineSnapshot();
            snapshot.setEnvironmentId(envId);
            snapshot.setTimestamp(LocalDateTime.now().toString());
            snapshot.setResourceSpecs(specs);
            
            return baselineRepository.save(snapshot);
        }
    }

    public Map<String, Object> compareLiveWithBaseline(String envId, String snapshotId, String ns, List<String> checks) throws Exception {
        SavedEnvironment env = environmentRepository.findById(envId)
                .orElseThrow(() -> new RuntimeException("Environment not found"));
        BaselineSnapshot snapshot = baselineRepository.findById(snapshotId)
                .orElseThrow(() -> new RuntimeException("Baseline snapshot not found"));

        try (KubernetesClient liveClient = clientFactory.createClient(env.getType(), env.getClusterUrl(), 
                                                                    env.getEncryptedToken(), env.getJumpHost(), 
                                                                    env.getJumpUser(), env.getEncryptedJumpPassword())) {
            
            Map<String, Object> diffResults = new HashMap<>();
            Map<String, List<Object>> baselineSpecs = snapshot.getResourceSpecs();

            if (checks.contains("DEPLOYMENTS") && baselineSpecs.containsKey("deployments")) {
                var live = liveClient.apps().deployments().inNamespace(ns).list().getItems();
                diffResults.put("deployments", comparisonService.compareLiveWithSpecs(live, baselineSpecs.get("deployments")));
            }
            if (checks.contains("CONFIGMAPS") && baselineSpecs.containsKey("configmaps")) {
                var live = liveClient.configMaps().inNamespace(ns).list().getItems();
                diffResults.put("configmaps", comparisonService.compareLiveWithSpecs(live, baselineSpecs.get("configmaps")));
            }
            if (checks.contains("SERVICES") && baselineSpecs.containsKey("services")) {
                var live = liveClient.services().inNamespace(ns).list().getItems();
                diffResults.put("services", comparisonService.compareLiveWithSpecs(live, baselineSpecs.get("services")));
            }
            if (checks.contains("PVC") && baselineSpecs.containsKey("pvcs")) {
                var live = liveClient.persistentVolumeClaims().inNamespace(ns).list().getItems();
                diffResults.put("pvcs", comparisonService.compareLiveWithSpecs(live, baselineSpecs.get("pvcs")));
            }
            if (checks.contains("VIRTUALSERVICES") && baselineSpecs.containsKey("virtualservices")) {
                var live = liveClient.resources(io.fabric8.istio.api.networking.v1alpha3.VirtualService.class).inNamespace(ns).list().getItems();
                diffResults.put("virtualservices", comparisonService.compareLiveWithSpecs(live, baselineSpecs.get("virtualservices")));
            }
            if (checks.contains("AUTH_POLICY") && baselineSpecs.containsKey("authpolicies")) {
                var live = liveClient.resources(io.fabric8.istio.api.security.v1beta1.AuthorizationPolicy.class).inNamespace(ns).list().getItems();
                diffResults.put("authpolicies", comparisonService.compareLiveWithSpecs(live, baselineSpecs.get("authpolicies")));
            }
            
            return diffResults;
        }
    }
}
