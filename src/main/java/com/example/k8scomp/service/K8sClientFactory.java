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
                                        String jumpHost, String jumpUser, String jumpPassword, String kubeconfig) throws Exception {
        log.info("Creating K8s client: type={}, clusterUrl={}, jumpHost={}", type, clusterUrl, jumpHost);

        if ("KUBECONFIG".equalsIgnoreCase(type)) {
            if (!org.springframework.util.StringUtils.hasText(kubeconfig)) {
                throw new IllegalArgumentException("Kubeconfig content is missing for KUBECONFIG connection type");
            }
            log.info("Creating client from kubeconfig content");
            return new KubernetesClientBuilder()
                    .withConfig(Config.fromKubeconfig(kubeconfig))
                    .build();
        }

        if ("JUMP".equalsIgnoreCase(type)) {
            log.info("Establishing SSH tunnel to {}@{}:22", jumpUser, jumpHost);
            if (!org.springframework.util.StringUtils.hasText(jumpHost) || !org.springframework.util.StringUtils.hasText(jumpUser)) {
                throw new IllegalArgumentException("Jump host or user missing for JUMP connection type");
            }
            
            Session session = null;
            try {
                JSch jsch = new JSch();
                session = jsch.getSession(jumpUser, jumpHost, 22);
                session.setPassword(org.springframework.util.StringUtils.hasText(jumpPassword) ? jumpPassword : "");
                session.setConfig("StrictHostKeyChecking", "no");
                session.connect();
                log.info("SSH session connected to {}", jumpHost);

                int localPort = new Random().nextInt(1000) + 7000;
                session.setPortForwardingL(localPort, org.springframework.util.StringUtils.hasText(clusterUrl) ? clusterUrl : "localhost", 6443);
                log.info("SSH port-forward established: localhost:{} → {}:6443", localPort, clusterUrl);

                clusterUrl = "https://localhost:" + localPort;
            } catch (Exception e) {
                if (session != null && session.isConnected()) session.disconnect();
                throw e;
            }
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
