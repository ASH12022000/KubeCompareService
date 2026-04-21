package com.example.k8scomp.service;

import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.element.Cell;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.util.Map;
import java.util.List;

@Service
public class ExportService {

    public byte[] generatePdfReport(Map<String, Object> results) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PdfWriter writer = new PdfWriter(out);
            PdfDocument pdf = new PdfDocument(writer);
            Document document = new Document(pdf);

            document.add(new Paragraph("Kubernetes Configuration Comparison Report")
                    .setFontSize(22)
                    .setBold()
                    .setFontColor(new com.itextpdf.kernel.colors.DeviceRgb(15, 23, 42))
                    .setMarginBottom(20));

            for (Map.Entry<String, Object> entry : results.entrySet()) {
                document.add(new Paragraph(entry.getKey().toUpperCase())
                        .setBold()
                        .setFontSize(14)
                        .setFontColor(new com.itextpdf.kernel.colors.DeviceRgb(56, 189, 248))
                        .setMarginTop(15)
                        .setMarginBottom(5));
                        
                List<Map<String, Object>> items = (List<Map<String, Object>>) entry.getValue();
                
                // Full width table with 3 columns: Resource Name (35%), Status (20%), Details (45%)
                Table table = new Table(com.itextpdf.layout.properties.UnitValue.createPercentArray(new float[]{35, 20, 45}));
                table.setWidth(com.itextpdf.layout.properties.UnitValue.createPercentValue(100));
                table.setMarginBottom(10);

                // Header Row
                com.itextpdf.kernel.colors.Color headerBg = new com.itextpdf.kernel.colors.DeviceRgb(241, 245, 249);
                table.addHeaderCell(new Cell().add(new Paragraph("Resource Name").setBold()).setBackgroundColor(headerBg).setPadding(8));
                table.addHeaderCell(new Cell().add(new Paragraph("Status").setBold()).setBackgroundColor(headerBg).setPadding(8));
                table.addHeaderCell(new Cell().add(new Paragraph("Details").setBold()).setBackgroundColor(headerBg).setPadding(8));

                for (Map<String, Object> item : items) {
                    String name = String.valueOf(item.get("name"));
                    String status = String.valueOf(item.get("status"));
                    
                    Cell nameCell = new Cell().add(new Paragraph(name)).setPadding(8);
                    Cell statusCell = new Cell().setPadding(8);
                    Cell detailsCell = new Cell().setPadding(8);

                    if ("MATCH".equals(status)) {
                        statusCell.add(new Paragraph("MATCH").setFontColor(com.itextpdf.kernel.colors.ColorConstants.GREEN).setBold());
                        detailsCell.add(new Paragraph("Resources are identical").setFontColor(com.itextpdf.kernel.colors.ColorConstants.GRAY));
                    } else if ("DIFFERENT".equals(status)) {
                        statusCell.add(new Paragraph("DIFFERENT").setFontColor(com.itextpdf.kernel.colors.ColorConstants.RED).setBold());
                        
                        Object v1 = item.containsKey("cluster1Value") ? item.get("cluster1Value") : item.get("baselineValue");
                        Object v2 = item.containsKey("cluster2Value") ? item.get("cluster2Value") : item.get("liveValue");

                        if (v1 != null && v2 != null) {
                            if (v1 instanceof String && v2 instanceof String) {
                                detailsCell.add(new Paragraph("Primary: " + v1 + "\nCompared: " + v2).setFontSize(9));
                            } else if (v1 instanceof Map && v2 instanceof Map) {
                                java.util.List<String> diffs = new java.util.ArrayList<>();
                                findDifferences((Map<?,?>) v1, (Map<?,?>) v2, "", diffs);
                                if (diffs.isEmpty()) {
                                    detailsCell.add(new Paragraph("Differences in system metadata (e.g. resourceVersion).").setFontSize(9).setFontColor(com.itextpdf.kernel.colors.ColorConstants.GRAY));
                                } else {
                                    String diffText = String.join("\n", diffs);
                                    if (diffs.size() >= 8) diffText += "\n...and more";
                                    detailsCell.add(new Paragraph(diffText).setFontSize(8));
                                }
                            } else {
                                detailsCell.add(new Paragraph("Configuration mismatch detected.").setFontSize(9));
                            }
                        } else if (item.containsKey("liveImage")) {
                            detailsCell.add(new Paragraph("Image changed to: " + item.get("liveImage")).setFontSize(9));
                        } else {
                            detailsCell.add(new Paragraph("Differences found").setFontSize(9));
                        }
                    } else {
                        statusCell.add(new Paragraph(status).setFontColor(com.itextpdf.kernel.colors.ColorConstants.ORANGE).setBold());
                        detailsCell.add(new Paragraph("Missing in one of the environments").setFontColor(com.itextpdf.kernel.colors.ColorConstants.GRAY).setFontSize(9));
                    }

                    table.addCell(nameCell);
                    table.addCell(statusCell);
                    table.addCell(detailsCell);
                }
                document.add(table);
            }

            document.close();
            return out.toByteArray();
        } catch (Exception e) {
            System.err.println("PDF Gen Error: " + e.getMessage());
            return new byte[0];
        }
    }

    private void findDifferences(Map<?, ?> m1, Map<?, ?> m2, String path, java.util.List<String> diffs) {
        if (m1 == null && m2 == null) return;
        if (m1 == null) { diffs.add(path + " added in Compared"); return; }
        if (m2 == null) { diffs.add(path + " removed in Compared"); return; }
        
        java.util.Set<Object> allKeys = new java.util.HashSet<>();
        allKeys.addAll(m1.keySet());
        allKeys.addAll(m2.keySet());
        
        for (Object key : allKeys) {
            if (diffs.size() >= 8) break; // Limit to top 8 diffs
            String keyStr = String.valueOf(key);
            
            Object v1 = m1.get(key);
            Object v2 = m2.get(key);
            
            String newPath = path.isEmpty() ? keyStr : path + "." + keyStr;
            
            if (v1 instanceof Map && v2 instanceof Map) {
                findDifferences((Map<?,?>) v1, (Map<?,?>) v2, newPath, diffs);
            } else if (!java.util.Objects.equals(v1, v2)) {
                // Ignore extremely noisy auto-generated Kubernetes metadata fields
                if (newPath.contains("managedFields") || newPath.contains("resourceVersion") || 
                    newPath.contains("generation") || newPath.contains("uid") || 
                    newPath.contains("creationTimestamp") || newPath.contains("lastTransitionTime")) {
                    continue;
                }
                diffs.add(newPath + ": " + formatValue(v1) + " -> " + formatValue(v2));
            }
        }
    }

    private String formatValue(Object v) {
        if (v == null) return "null";
        String s = v.toString();
        if (s.length() > 30) return s.substring(0, 27) + "...";
        return s;
    }
}
