package dev.infrai.reports.service;

import dev.infrai.reports.domain.DeliveryDecision;
import dev.infrai.reports.domain.ReleaseReport;
import dev.infrai.reports.email.ReportMailer;
import dev.infrai.reports.pdf.PdfReportRenderer;

import java.util.Base64;

public final class ReleaseReportService {
    private final PdfReportRenderer renderer;
    private final ReportMailer mailer;

    public ReleaseReportService(PdfReportRenderer renderer, ReportMailer mailer) {
        this.renderer = renderer;
        this.mailer = mailer;
    }

    public Outcome deliver(ReleaseReport report) {
        DeliveryDecision decision = DeliveryDecision.evaluate(report);
        byte[] pdf = renderer.render(report);
        if (!decision.allowed()) return new Outcome(decision, pdf, null);

        String encodedPdf = Base64.getEncoder().encodeToString(pdf);
        String html = "<h1>Release " + escape(report.release().version()) + "</h1>"
                + "<p>Build " + escape(report.build().buildId()) + " passed release checks.</p>"
                + "<p><a download=\"release-report.pdf\" href=\"data:application/pdf;base64,"
                + encodedPdf + "\">Download the PDF report</a></p>";
        ReportMailer.SendReceipt receipt = mailer.send(
                report.recipient(),
                "Release report " + report.release().version(),
                html,
                "release-report-" + report.reportId());
        return new Outcome(decision, pdf, receipt.messageId());
    }

    private String escape(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }

    public record Outcome(DeliveryDecision decision, byte[] pdf, String messageId) {}
}
