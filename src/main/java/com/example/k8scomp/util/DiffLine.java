package com.example.k8scomp.util;

public class DiffLine {
    private String cluster1;
    private String cluster2;
    private boolean match;

    public String getCluster1() { return cluster1; }
    public void setCluster1(String cluster1) { this.cluster1 = cluster1; }
    public String getCluster2() { return cluster2; }
    public void setCluster2(String cluster2) { this.cluster2 = cluster2; }
    public boolean isMatch() { return match; }
    public void setMatch(boolean match) { this.match = match; }
}
