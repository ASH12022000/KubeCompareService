package com.example.k8scomp.service;

import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
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
                    .setFontSize(20).setBold());

            for (Map.Entry<String, Object> entry : results.entrySet()) {
                document.add(new Paragraph(entry.getKey().toUpperCase()).setBold());
                List<Map<String, Object>> items = (List<Map<String, Object>>) entry.getValue();
                
                Table table = new Table(3);
                table.addCell("Resource Name");
                table.addCell("Status");
                table.addCell("Matches");

                for (Map<String, Object> item : items) {
                    table.addCell(String.valueOf(item.get("name")));
                    table.addCell(String.valueOf(item.get("status")));
                    table.addCell(String.valueOf(item.get("status").equals("MATCH")));
                }
                document.add(table);
            }

            document.close();
            return out.toByteArray();
        } catch (Exception e) {
            return new byte[0];
        }
    }
}
