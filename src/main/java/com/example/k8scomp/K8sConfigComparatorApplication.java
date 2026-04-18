package com.example.k8scomp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class K8sConfigComparatorApplication {
    public static void main(String[] args) {
        SpringApplication.run(K8sConfigComparatorApplication.class, args);
    }
}
