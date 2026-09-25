package dev.infrai.reports.domain;

public record DeliveryDecision(boolean allowed, String reason) {
    public static DeliveryDecision evaluate(ReleaseReport report) {
        boolean hasHighDiagnostic = report.diagnostics().stream()
                .anyMatch(item -> item.severity() == ReleaseReport.Diagnostic.Severity.HIGH);
        if (report.release().status() != ReleaseReport.ReleaseOperation.Status.SUCCEEDED) {
            return new DeliveryDecision(false, "release is not in SUCCEEDED state");
        }
        if (hasHighDiagnostic) {
            return new DeliveryDecision(false, "high-severity diagnostics require review");
        }
        return new DeliveryDecision(true, "release checks passed");
    }
}
