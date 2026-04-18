package com.example.k8scomp.model;

import lombok.Data;

@Data
public class CategoryResult {
    private String category;
    private boolean match;
    private Object details;
}
