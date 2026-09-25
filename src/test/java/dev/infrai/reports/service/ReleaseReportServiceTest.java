package dev.infrai.reports.service;

import dev.infrai.reports.domain.ReleaseReport;
import dev.infrai.reports.email.ReportMailer;
import dev.infrai.reports.pdf.PdfReportRenderer;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public final class ReleaseReportServiceTest {
    public static void main(String[] args) {
        AtomicInteger sends = new AtomicInteger();
        ReportMailer mailer = (recipient, subject, html, key) -> {
            sends.incrementAndGet();
            assert key.equals("release-report-rpt-42");
            assert html.contains("data:application/pdf;base64,");
            return new ReportMailer.SendReceipt("msg-123");
        };
        ReleaseReportService service = new ReleaseReportService(new PdfReportRenderer(), mailer);

        ReleaseReportService.Outcome approved = service.deliver(report(ReleaseReport.Diagnostic.Severity.WARNING));
        assert approved.decision().allowed();
        assert approved.messageId().equals("msg-123");
        assert new String(approved.pdf(), 0, 8).equals("%PDF-1.4");
        assert sends.get() == 1;

        ReleaseReportService.Outcome held = service.deliver(report(ReleaseReport.Diagnostic.Severity.HIGH));
        assert !held.decision().allowed();
        assert held.messageId() == null;
        assert sends.get() == 1 : "held report must not send mail";
        System.out.println("release report policy: PASS");
    }

    private static ReleaseReport report(ReleaseReport.Diagnostic.Severity severity) {
        return new ReleaseReport(
                "rpt-42",
                "tools@example.com",
                new ReleaseReport.BuildEvent("build-884", "ledger-api", "8d31e2a", Instant.parse("2026-09-24T10:15:30Z")),
                new ReleaseReport.ReleaseOperation("2026.09.24", "production", ReleaseReport.ReleaseOperation.Status.SUCCEEDED),
                List.of(new ReleaseReport.Diagnostic(severity, "schema-check", "migration checksum matched")));
    }
}
