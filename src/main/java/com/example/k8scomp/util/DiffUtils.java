package com.example.k8scomp.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.util.*;

public class DiffUtils {

    private static final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());

    public static String toYaml(Object obj) {
        try {
            return yamlMapper.writeValueAsString(obj);
        } catch (Exception e) {
            return "Error converting to YAML";
        }
    }

    public static List<DiffLine> getDiff(String yaml1, String yaml2) {
        String[] lines1 = yaml1.split("\n");
        String[] lines2 = yaml2.split("\n");
        List<DiffLine> diff = new ArrayList<>();
        
        int max = Math.max(lines1.length, lines2.length);
        for (int i = 0; i < max; i++) {
            String l1 = i < lines1.length ? lines1[i] : null;
            String l2 = i < lines2.length ? lines2[i] : null;
            
            DiffLine line = new DiffLine();
            line.setCluster1(l1);
            line.setCluster2(l2);
            line.setMatch(Objects.equals(l1, l2));
            diff.add(line);
        }
        return diff;
    }
}
