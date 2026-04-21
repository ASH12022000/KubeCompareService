package com.example.k8scomp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.convert.MappingMongoConverter;

@SpringBootApplication
@EnableScheduling
public class K8sConfigComparatorApplication {
    public static void main(String[] args) {
        SpringApplication.run(K8sConfigComparatorApplication.class, args);
    }

    @Autowired
    public void configureMongoConverter(MappingMongoConverter converter) {
        converter.setMapKeyDotReplacement("_DOT_");
    }
}
