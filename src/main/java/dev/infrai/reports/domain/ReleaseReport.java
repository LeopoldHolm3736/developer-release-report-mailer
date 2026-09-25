package dev.infrai.reports.domain;

import java.time.Instant;
import java.util.List;

public record ReleaseReport(
        String reportId,
        String recipient,
        BuildEvent build,
        ReleaseOperation release,
        List<Diagnostic> diagnostics) {

    public ReleaseReport {
        diagnostics = List.copyOf(diagnostics);
    }

    public record BuildEvent(String buildId, String repository, String commit, Instant completedAt) {}

    public record ReleaseOperation(String version, String environment, Status status) {
        public enum Status { SUCCEEDED, FAILED, ROLLED_BACK }
    }

    public record Diagnostic(Severity severity, String component, String message) {
        public enum Severity { INFO, WARNING, HIGH }
    }
}
