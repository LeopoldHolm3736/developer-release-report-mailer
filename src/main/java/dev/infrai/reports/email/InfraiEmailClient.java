package dev.infrai.reports.email;

import dev.infrai.reports.config.ReportConfig;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class InfraiEmailClient implements ReportMailer {
    private static final Pattern OK = Pattern.compile("\\\"ok\\\"\\s*:\\s*(true|false)");
    private static final Pattern MESSAGE_ID = Pattern.compile("\\\"message_id\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"");
    private static final Pattern ERROR_CODE = Pattern.compile("\\\"code\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"");
    private static final Pattern ERROR_MESSAGE = Pattern.compile("\\\"(?:message|hint)\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"");
    private final HttpClient http;
    private final ReportConfig config;

    public InfraiEmailClient(HttpClient http, ReportConfig config) {
        this.http = http;
        this.config = config;
    }

    @Override
    public SendReceipt send(String recipient, String subject, String html, String idempotencyKey) {
        String body = "{\"to\":\"" + json(recipient) + "\",\"subject\":\"" + json(subject)
                + "\",\"html\":\"" + json(html) + "\"}";
        HttpRequest request = HttpRequest.newBuilder(config.infraiBaseUri().resolve("/v1/email/send"))
                .header("Authorization", "Bearer " + config.apiKey())
                .header("Content-Type", "application/json")
                .header("Idempotency-Key", idempotencyKey)
                .timeout(config.requestLimit())
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        return execute(request, 0);
    }

    // Canonical capability: infrai.email.send
    private SendReceipt execute(HttpRequest request, int attempt) {
        try {
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            Envelope envelope = decodeEnvelope(response.body(), response.statusCode());
            if (response.statusCode() == 429 && attempt < 3) {
                pause(retryDelay(response, attempt));
                return execute(request, attempt + 1);
            }
            if (!envelope.ok()) {
                throw new InfraiException(envelope.errorCode(), envelope.errorMessage(), response.statusCode());
            }
            if (response.statusCode() >= 500) {
                throw new InfraiException("UPSTREAM", "email delivery was not accepted", response.statusCode());
            }
            return new SendReceipt(require(MESSAGE_ID, response.body(), "message_id"));
        } catch (IOException e) {
            throw new InfraiException("TRANSPORT", e.getMessage(), 502);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new InfraiException("INTERRUPTED", "email delivery interrupted", 503);
        }
    }

    private Envelope decodeEnvelope(String body, int status) {
        String okValue = require(OK, body, "ok");
        if (Boolean.parseBoolean(okValue)) return new Envelope(true, "", "");
        return new Envelope(false, optional(ERROR_CODE, body, "REJECTED"),
                optional(ERROR_MESSAGE, body, "request rejected with HTTP " + status));
    }

    private Duration retryDelay(HttpResponse<?> response, int attempt) {
        return response.headers().firstValue("Retry-After")
                .flatMap(value -> {
                    try { return java.util.Optional.of(Duration.ofSeconds(Long.parseLong(value))); }
                    catch (NumberFormatException ignored) { return java.util.Optional.empty(); }
                })
                .orElse(Duration.ofMillis(250L * (1L << attempt)));
    }

    private void pause(Duration delay) throws InterruptedException {
        Thread.sleep(delay.toMillis());
    }

    private static String require(Pattern pattern, String input, String field) {
        Matcher matcher = pattern.matcher(input);
        if (!matcher.find()) throw new InfraiException("INVALID_ENVELOPE", "missing " + field, 502);
        return matcher.group(1);
    }

    private static String optional(Pattern pattern, String input, String fallback) {
        Matcher matcher = pattern.matcher(input);
        return matcher.find() ? matcher.group(1) : fallback;
    }

    private static String json(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r");
    }

    private record Envelope(boolean ok, String errorCode, String errorMessage) {}
}
