package com.example.k8scomp.controller;

import com.example.k8scomp.model.SavedEnvironment;
import java.util.List;

public class ComparisonRequest {
    private SavedEnvironment env1;
    private SavedEnvironment env2;
    private String ns1;
    private String ns2;
    private List<String> checks;
    private List<String> exclusions;
    private String userId;

    public SavedEnvironment getEnv1() { return env1; }
    public void setEnv1(SavedEnvironment env1) { this.env1 = env1; }
    public SavedEnvironment getEnv2() { return env2; }
    public void setEnv2(SavedEnvironment env2) { this.env2 = env2; }
    public String getNs1() { return ns1; }
    public void setNs1(String ns1) { this.ns1 = ns1; }
    public String getNs2() { return ns2; }
    public void setNs2(String ns2) { this.ns2 = ns2; }
    public List<String> getChecks() { return checks; }
    public void setChecks(List<String> checks) { this.checks = checks; }
    public List<String> getExclusions() { return exclusions; }
    public void setExclusions(List<String> exclusions) { this.exclusions = exclusions; }
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
}
