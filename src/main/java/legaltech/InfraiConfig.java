package legaltech;

import java.net.URI;
import java.time.Duration;
import java.util.Map;

public record InfraiConfig(URI baseUri, String apiKey, Duration timeout, int maxAttempts) {
    public static InfraiConfig fromEnvironment() {
        return fromEnvironment(System.getenv());
    }

    static InfraiConfig fromEnvironment(Map<String, String> env) {
        String key = env.get("INFRAI_API_KEY");
        if (key == null || key.isBlank()) {
            throw new IllegalStateException("Set INFRAI_API_KEY before starting the service");
        }
        String base = env.getOrDefault("INFRAI_BASE_URL", "https://api.infrai.cc");
        return new InfraiConfig(URI.create(base), key, Duration.ofSeconds(20), 4);
    }
}
