package legaltech;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class InfraiErrorsClient implements AgentFailureSink {
    private static final String CAPTURE_PATH = "/v1/errors/capture";
    private final InfraiConfig config;
    private final HttpClient http;

    public InfraiErrorsClient(InfraiConfig config) {
        this(config, HttpClient.newBuilder().connectTimeout(config.timeout()).build());
    }

    InfraiErrorsClient(InfraiConfig config, HttpClient http) {
        this.config = config;
        this.http = http;
    }

    @Override
    public CaptureReceipt capture(String title, String message, String exception, String idempotencyKey,
                                  Map<String, String> context) {
        String payload = "{" +
                "\"title\":" + json(title) + "," +
                "\"message\":" + json(message) + "," +
                "\"level\":\"error\"," +
                "\"fingerprint\":[\"legal-matter-agent\"," + json(context.get("step")) + "]," +
                "\"exception\":" + json(exception) + "," +
                "\"context\":" + jsonObject(context) + "}";

        for (int attempt = 1; attempt <= config.maxAttempts(); attempt++) {
            HttpRequest request = HttpRequest.newBuilder(config.baseUri().resolve(CAPTURE_PATH))
                    .timeout(config.timeout())
                    .header("Authorization", "Bearer " + config.apiKey())
                    .header("Content-Type", "application/json")
                    .header("Idempotency-Key", idempotencyKey)
                    .method("POST", HttpRequest.BodyPublishers.ofString(payload))
                    .build();
            try {
                HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
                Envelope envelope = Envelope.parse(response.body());
                if (response.statusCode() == 429 && attempt < config.maxAttempts()) {
                    sleep(retryDelay(response, attempt));
                    continue;
                }
                if (!envelope.ok()) {
                    throw new InfraiApiException(envelope.errorCode(), envelope.errorMessage(), response.statusCode());
                }
                if (response.statusCode() >= 500) {
                    throw new IllegalStateException("Infrai transport response " + response.statusCode());
                }
                return new CaptureReceipt(envelope.dataString("event_id"), envelope.dataString("error_group_id"));
            } catch (IOException e) {
                throw new IllegalStateException("Could not reach the error capture service", e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while capturing the agent failure", e);
            }
        }
        throw new IllegalStateException("Retry budget exhausted");
    }

    private static Duration retryDelay(HttpResponse<?> response, int attempt) {
        String value = response.headers().firstValue("Retry-After").orElse("");
        try {
            return Duration.ofSeconds(Math.max(1, Long.parseLong(value)));
        } catch (NumberFormatException ignored) {
            return Duration.ofMillis(250L * (1L << (attempt - 1)));
        }
    }

    private static void sleep(Duration delay) throws InterruptedException {
        Thread.sleep(delay.toMillis());
    }

    private static String jsonObject(Map<String, String> values) {
        StringBuilder out = new StringBuilder("{");
        for (Map.Entry<String, String> entry : values.entrySet()) {
            if (out.length() > 1) out.append(',');
            out.append(json(entry.getKey())).append(':').append(json(entry.getValue()));
        }
        return out.append('}').toString();
    }

    private static String json(String value) {
        StringBuilder out = new StringBuilder("\"");
        for (char c : value.toCharArray()) {
            switch (c) {
                case '\\' -> out.append("\\\\");
                case '"' -> out.append("\\\"");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> out.append(c);
            }
        }
        return out.append('"').toString();
    }

    private record Envelope(boolean ok, String body, String errorCode, String errorMessage) {
        private static final Pattern OK = Pattern.compile("\\\"ok\\\"\\s*:\\s*(true|false)");

        static Envelope parse(String body) {
            Matcher matcher = OK.matcher(body);
            if (!matcher.find()) throw new IllegalStateException("Response is not an Infrai envelope");
            return new Envelope(Boolean.parseBoolean(matcher.group(1)), body,
                    find(body, "code", "UNKNOWN"), find(body, "message", "Request rejected"));
        }

        String dataString(String name) { return find(body, name, ""); }

        private static String find(String source, String name, String fallback) {
            Pattern pattern = Pattern.compile("\\\"" + Pattern.quote(name) + "\\\"\\s*:\\s*\\\"((?:\\\\.|[^\\\"])*)\\\"");
            Matcher matcher = pattern.matcher(source);
            return matcher.find() ? matcher.group(1).replace("\\\"", "\"").replace("\\\\", "\\") : fallback;
        }
    }
}
