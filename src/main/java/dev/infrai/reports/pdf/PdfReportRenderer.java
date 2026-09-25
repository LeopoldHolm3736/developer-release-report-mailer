package dev.infrai.reports.pdf;

import dev.infrai.reports.domain.ReleaseReport;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public final class PdfReportRenderer {
    public byte[] render(ReleaseReport report) {
        List<String> lines = new ArrayList<>();
        lines.add("Developer Release Report");
        lines.add("Report: " + report.reportId());
        lines.add("Repository: " + report.build().repository());
        lines.add("Build: " + report.build().buildId());
        lines.add("Commit: " + report.build().commit());
        lines.add("Completed: " + report.build().completedAt());
        lines.add("Release: " + report.release().version());
        lines.add("Environment: " + report.release().environment());
        lines.add("Status: " + report.release().status());
        lines.add("Diagnostics:");
        report.diagnostics().forEach(item -> lines.add(
                "[" + item.severity() + "] " + item.component() + ": " + item.message()));

        StringBuilder stream = new StringBuilder("BT\n/F1 11 Tf\n50 760 Td\n");
        for (int i = 0; i < lines.size(); i++) {
            if (i > 0) stream.append("0 -18 Td\n");
            stream.append('(').append(pdfEscape(lines.get(i))).append(") Tj\n");
        }
        stream.append("ET\n");
        return assemblePdf(stream.toString());
    }

    private byte[] assemblePdf(String content) {
        List<String> objects = List.of(
                "<< /Type /Catalog /Pages 2 0 R >>",
                "<< /Type /Pages /Kids [3 0 R] /Count 1 >>",
                "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] /Resources << /Font << /F1 5 0 R >> >> /Contents 4 0 R >>",
                "<< /Length " + content.getBytes(StandardCharsets.US_ASCII).length + " >>\nstream\n" + content + "endstream",
                "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        write(out, "%PDF-1.4\n");
        List<Integer> offsets = new ArrayList<>();
        for (int i = 0; i < objects.size(); i++) {
            offsets.add(out.size());
            write(out, (i + 1) + " 0 obj\n" + objects.get(i) + "\nendobj\n");
        }
        int xref = out.size();
        write(out, "xref\n0 " + (objects.size() + 1) + "\n0000000000 65535 f \n");
        offsets.forEach(offset -> write(out, String.format("%010d 00000 n \n", offset)));
        write(out, "trailer\n<< /Size " + (objects.size() + 1) + " /Root 1 0 R >>\nstartxref\n" + xref + "\n%%EOF\n");
        return out.toByteArray();
    }

    private void write(ByteArrayOutputStream out, String value) {
        out.writeBytes(value.getBytes(StandardCharsets.US_ASCII));
    }

    private String pdfEscape(String value) {
        return value.replace("\\", "\\\\").replace("(", "\\(").replace(")", "\\)")
                .replaceAll("[^\\x20-\\x7E]", "?");
    }
}
