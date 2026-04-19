package com.example.k8scomp.service;

import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.ConfigBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import java.util.Random;

@Component
public class K8sClientFactory {

    private static final Logger log = LoggerFactory.getLogger(K8sClientFactory.class);

    public KubernetesClient createClient(String type, String clusterUrl, String token,
                                        String jumpHost, String jumpUser, String jumpPassword) throws Exception {
        log.info("Creating K8s client: type={}, clusterUrl={}, jumpHost={}", type, clusterUrl, jumpHost);

        if ("JUMP".equalsIgnoreCase(type)) {
            log.info("Establishing SSH tunnel to {}@{}:22", jumpUser, jumpHost);
            JSch jsch = new JSch();
            Session session = jsch.getSession(jumpUser, jumpHost, 22);
            session.setPassword(jumpPassword);
            session.setConfig("StrictHostKeyChecking", "no");
            session.connect();
            log.info("SSH session connected to {}", jumpHost);

            int localPort = new Random().nextInt(1000) + 7000;
            session.setPortForwardingL(localPort, clusterUrl, 6443);
            log.info("SSH port-forward established: localhost:{} → {}:6443", localPort, clusterUrl);

            clusterUrl = "https://localhost:" + localPort;
        }

        Config config = new ConfigBuilder()
                .withMasterUrl(clusterUrl)
                .withOauthToken(token)
                .withTrustCerts(true)
                .build();

        log.debug("K8s client config built for masterUrl={}", clusterUrl);
        return new KubernetesClientBuilder().withConfig(config).build();
    }
}
