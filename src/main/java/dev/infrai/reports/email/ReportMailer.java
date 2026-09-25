package dev.infrai.reports.email;

public interface ReportMailer {
    SendReceipt send(String recipient, String subject, String html, String idempotencyKey);

    record SendReceipt(String messageId) {}
}
