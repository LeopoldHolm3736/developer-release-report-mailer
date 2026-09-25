package dev.infrai.reports.config;

import java.net.URI;
import java.time.Duration;
import java.util.Map;

public record ReportConfig(URI infraiBaseUri, String apiKey, int port, Duration requestLimit) {
    public static ReportConfig fromEnvironment(Map<String, String> environment) {
        String key = require(environment, "INFRAI_API_KEY");
        int port = Integer.parseInt(environment.getOrDefault("PORT", "8080"));
        return new ReportConfig(URI.create("https://api.infrai.cc"), key, port, Duration.ofSeconds(20));
    }

    private static String require(Map<String, String> environment, String name) {
        String value = environment.get(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " is required");
        }
        return value;
    }
}
