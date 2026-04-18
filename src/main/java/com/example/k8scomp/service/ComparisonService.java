package com.example.k8scomp.service;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.client.KubernetesClient;
import java.util.*;
import java.util.stream.Collectors;

@org.springframework.stereotype.Service
public class ComparisonService {

    public List<Map<String, Object>> compareDeployments(KubernetesClient c1, KubernetesClient c2, String ns1, String ns2) {
        List<Deployment> deps1 = c1.apps().deployments().inNamespace(ns1).list().getItems();
        List<Deployment> deps2 = c2.apps().deployments().inNamespace(ns2).list().getItems();
        return compareResources(deps1, deps2);
    }

    public List<Map<String, Object>> compareConfigMaps(KubernetesClient c1, KubernetesClient c2, String ns1, String ns2) {
        List<ConfigMap> cms1 = c1.configMaps().inNamespace(ns1).list().getItems();
        List<ConfigMap> cms2 = c2.configMaps().inNamespace(ns2).list().getItems();
        return compareResources(cms1, cms2);
    }

    public List<Map<String, Object>> compareServices(KubernetesClient c1, KubernetesClient c2, String ns1, String ns2) {
        List<Service> svcs1 = c1.services().inNamespace(ns1).list().getItems();
        List<Service> svcs2 = c2.services().inNamespace(ns2).list().getItems();
        return compareResources(svcs1, svcs2);
    }

    public List<Map<String, Object>> comparePVCs(KubernetesClient c1, KubernetesClient c2, String ns1, String ns2) {
        List<io.fabric8.kubernetes.api.model.PersistentVolumeClaim> pvcs1 = c1.persistentVolumeClaims().inNamespace(ns1).list().getItems();
        List<io.fabric8.kubernetes.api.model.PersistentVolumeClaim> pvcs2 = c2.persistentVolumeClaims().inNamespace(ns2).list().getItems();
        return compareResources(pvcs1, pvcs2);
    }

    public List<Map<String, Object>> compareImages(KubernetesClient c1, KubernetesClient c2, String ns1, String ns2) {
        List<Deployment> deps1 = c1.apps().deployments().inNamespace(ns1).list().getItems();
        List<Deployment> deps2 = c2.apps().deployments().inNamespace(ns2).list().getItems();
        
        Map<String, String> images1 = extractImageTags(deps1);
        Map<String, String> images2 = extractImageTags(deps2);
        
        List<Map<String, Object>> results = new ArrayList<>();
        images1.forEach((name, tag1) -> {
            Map<String, Object> diff = new HashMap<>();
            diff.put("name", name);
            String tag2 = images2.get(name);
            if (tag2 == null) {
                diff.put("status", "ONLY_IN_CLUSTER_1");
            } else {
                diff.put("status", tag1.equals(tag2) ? "MATCH" : "DIFFERENT");
                diff.put("cluster1Value", tag1);
                diff.put("cluster2Value", tag2);
            }
            results.add(diff);
        });
        return results;
    }

    public List<Map<String, Object>> compareIstio(KubernetesClient c1, KubernetesClient c2, String ns1, String ns2) {
        List<Map<String, Object>> istioResults = new ArrayList<>();
        try {
            var vs1 = c1.resources(io.fabric8.istio.api.networking.v1alpha3.VirtualService.class).inNamespace(ns1).list().getItems();
            var vs2 = c2.resources(io.fabric8.istio.api.networking.v1alpha3.VirtualService.class).inNamespace(ns2).list().getItems();
            istioResults.addAll(compareResources(vs1, vs2));

            var ap1 = c1.resources(io.fabric8.istio.api.security.v1beta1.AuthorizationPolicy.class).inNamespace(ns1).list().getItems();
            var ap2 = c2.resources(io.fabric8.istio.api.security.v1beta1.AuthorizationPolicy.class).inNamespace(ns2).list().getItems();
            istioResults.addAll(compareResources(ap1, ap2));
        } catch (Exception e) {
            System.err.println("Istio CRDs might be missing: " + e.getMessage());
        }
        return istioResults;
    }

    public List<Map<String, Object>> compareLiveWithSpecs(List<? extends HasMetadata> liveResources, List<Object> baselineSpecs) {
        List<Map<String, Object>> results = new ArrayList<>();
        Map<String, Object> baselineMap = new HashMap<>();
        
        // Convert baseline specs to a map for easy lookup by name
        for (Object spec : baselineSpecs) {
            if (spec instanceof Map) {
                Map<String, Object> metadata = (Map<String, Object>) ((Map<String, Object>) spec).get("metadata");
                if (metadata != null) baselineMap.put((String) metadata.get("name"), spec);
            } else if (spec instanceof HasMetadata) {
                baselineMap.put(((HasMetadata) spec).getMetadata().getName(), spec);
            }
        }

        for (HasMetadata live : liveResources) {
            String name = live.getMetadata().getName();
            Object baseline = baselineMap.get(name);
            Map<String, Object> diff = new HashMap<>();
            diff.put("name", name);
            
            if (baseline == null) {
                diff.put("status", "ONLY_IN_LIVE");
            } else {
                // Simplified comparison: compare string representations
                boolean equal = Objects.equals(live.toString(), baseline.toString());
                diff.put("status", equal ? "MATCH" : "DIFFERENT");
                diff.put("liveValue", live);
                diff.put("baselineValue", baseline);
            }
            results.add(diff);
        }
        return results;
    }

    private <T extends HasMetadata> List<Map<String, Object>> compareResources(List<T> list1, List<T> list2) {
        List<Map<String, Object>> results = new ArrayList<>();
        Map<String, T> map2 = list2.stream().collect(Collectors.toMap(r -> r.getMetadata().getName(), r -> r));

        for (T r1 : list1) {
            String name = r1.getMetadata().getName();
            T r2 = map2.get(name);
            Map<String, Object> diff = new HashMap<>();
            diff.put("name", name);
            if (r2 == null) {
                diff.put("status", "ONLY_IN_CLUSTER_1");
            } else {
                boolean equal = Objects.equals(r1.toString(), r2.toString());
                diff.put("status", equal ? "MATCH" : "DIFFERENT");
                diff.put("cluster1Value", r1);
                diff.put("cluster2Value", r2);
            }
            results.add(diff);
        }
        return results;
    }

    private Map<String, String> extractImageTags(List<Deployment> deps) {
        Map<String, String> images = new HashMap<>();
        for (Deployment d : deps) {
            d.getSpec().getTemplate().getSpec().getContainers().forEach(c -> {
                images.put(d.getMetadata().getName() + "/" + c.getName(), c.getImage());
            });
        }
        return images;
    }
}
