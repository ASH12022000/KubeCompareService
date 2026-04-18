package com.example.k8scomp.service;

import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.ConfigBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import org.springframework.stereotype.Component;
import java.util.Random;

@Component
public class K8sClientFactory {

    public KubernetesClient createClient(String type, String clusterUrl, String token, 
                                        String jumpHost, String jumpUser, String jumpPassword) throws Exception {
        if ("JUMP".equalsIgnoreCase(type)) {
            JSch jsch = new JSch();
            Session session = jsch.getSession(jumpUser, jumpHost, 22);
            session.setPassword(jumpPassword);
            session.setConfig("StrictHostKeyChecking", "no");
            session.connect();
            
            int localPort = new Random().nextInt(1000) + 7000;
            session.setPortForwardingL(localPort, clusterUrl, 6443);
            
            clusterUrl = "https://localhost:" + localPort;
        }

        Config config = new ConfigBuilder()
                .withMasterUrl(clusterUrl)
                .withOauthToken(token)
                .withTrustCerts(true)
                .build();
        
        return new KubernetesClientBuilder().withConfig(config).build();
    }
}
