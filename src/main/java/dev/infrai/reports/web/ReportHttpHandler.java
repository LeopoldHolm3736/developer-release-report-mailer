package dev.infrai.reports.web;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import dev.infrai.reports.domain.ReleaseReport;
import dev.infrai.reports.email.InfraiException;
import dev.infrai.reports.service.ReleaseReportService;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ReportHttpHandler implements HttpHandler {
    private final ReleaseReportService service;
    private final Map<String, byte[]> reports = new ConcurrentHashMap<>();

    public ReportHttpHandler(ReleaseReportService service) {
        this.service = service;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        try {
            if (exchange.getRequestMethod().equals("POST") && exchange.getRequestURI().getPath().equals("/reports/email")) {
                create(exchange);
                return;
            }
            if (exchange.getRequestMethod().equals("GET") && exchange.getRequestURI().getPath().startsWith("/reports/")) {
                download(exchange);
                return;
            }
            respond(exchange, 404, "application/json", "{\"error\":\"route not found\"}".getBytes(StandardCharsets.UTF_8));
        } catch (IllegalArgumentException e) {
            respond(exchange, 400, "application/json", jsonError(e.getMessage()));
        } catch (InfraiException e) {
            int status = e.status() >= 400 && e.status() < 500 ? e.status() : 502;
            respond(exchange, status, "application/json", jsonError(e.code() + ": " + e.getMessage()));
        }
    }

    private void create(HttpExchange exchange) throws IOException {
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        ReleaseReport report = parse(body);
        ReleaseReportService.Outcome outcome = service.deliver(report);
        reports.put(report.reportId(), outcome.pdf());
        int status = outcome.decision().allowed() ? 202 : 422;
        String response = "{\"report_id\":\"" + quote(report.reportId()) + "\",\"delivery\":\""
                + (outcome.decision().allowed() ? "SENT" : "HELD") + "\",\"reason\":\""
                + quote(outcome.decision().reason()) + "\",\"message_id\":"
                + (outcome.messageId() == null ? "null" : "\"" + quote(outcome.messageId()) + "\"")
                + ",\"pdf\":\"/reports/" + quote(report.reportId()) + "\"}";
        respond(exchange, status, "application/json", response.getBytes(StandardCharsets.UTF_8));
    }

    private void download(HttpExchange exchange) throws IOException {
        String reportId = exchange.getRequestURI().getPath().substring("/reports/".length());
        byte[] pdf = reports.get(reportId);
        if (pdf == null) {
            respond(exchange, 404, "application/json", jsonError("report not found"));
            return;
        }
        exchange.getResponseHeaders().set("Content-Disposition", "attachment; filename=release-report.pdf");
        respond(exchange, 200, "application/pdf", pdf);
    }

    private ReleaseReport parse(String json) {
        String reportId = field(json, "report_id");
        String recipient = field(json, "recipient");
        ReleaseReport.BuildEvent build = new ReleaseReport.BuildEvent(
                field(json, "build_id"), field(json, "repository"), field(json, "commit"),
                Instant.parse(field(json, "completed_at")));
        ReleaseReport.ReleaseOperation release = new ReleaseReport.ReleaseOperation(
                field(json, "version"), field(json, "environment"),
                ReleaseReport.ReleaseOperation.Status.valueOf(field(json, "status")));
        ReleaseReport.Diagnostic diagnostic = new ReleaseReport.Diagnostic(
                ReleaseReport.Diagnostic.Severity.valueOf(field(json, "severity")),
                field(json, "component"), field(json, "message"));
        return new ReleaseReport(reportId, recipient, build, release, List.of(diagnostic));
    }

    private String field(String json, String name) {
        Pattern pattern = Pattern.compile("\\\"" + Pattern.quote(name) + "\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"");
        Matcher matcher = pattern.matcher(json);
        if (!matcher.find()) throw new IllegalArgumentException("missing field: " + name);
        return matcher.group(1);
    }

    private byte[] jsonError(String message) {
        return ("{\"error\":\"" + quote(message) + "\"}").getBytes(StandardCharsets.UTF_8);
    }

    private String quote(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private void respond(HttpExchange exchange, int status, String contentType, byte[] body) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.sendResponseHeaders(status, body.length);
        exchange.getResponseBody().write(body);
        exchange.close();
    }
}
