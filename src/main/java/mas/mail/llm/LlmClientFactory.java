package mas.mail.llm;

import mas.mail.config.AppConfig;

import java.net.URI;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * creates the {@link LlmClient} described in the config
 * new engines are added with {@link #register(String, Creator)} without changing the rest of the code
 * <p>
 * it also keeps emails private
 * unless {@code llm.allowRemote=true}, only engines on this computer or on a private network are allowed
 */
public final class LlmClientFactory {

    /** creates a client for a config and a model name */
    @FunctionalInterface
    public interface Creator {
        LlmClient create(AppConfig config, String model);
    }

    private final Map<String, Creator> creators = new LinkedHashMap<>();
    private final AppConfig config;

    public LlmClientFactory(AppConfig config) {
        this.config = config;
        register("ollama", (c, m) -> new OllamaClient(c.get("llm.baseUrl", "http://localhost:11434"), m,
                Duration.ofSeconds(c.getInt("llm.timeoutSeconds", 300)), c.getInt("llm.contextWindow", 4096), c.get("llm.think", "")));
        register("openai", (c, m) -> new OpenAiCompatibleClient(c.get("llm.baseUrl", "http://localhost:1234"), m,
                Duration.ofSeconds(c.getInt("llm.timeoutSeconds", 300))));
        register("mock", (c, m) -> new MockLlmClient());
    }

    public void register(String provider, Creator creator) {
        creators.put(provider, creator);
    }

    /** client with the global model from the config */
    public LlmClient create() {
        return create("");
    }

    /** client with the given model (or the global one if empty) */
    public LlmClient create(String modelOverride) {
        String provider = config.get("llm.provider", "ollama");
        Creator creator = creators.get(provider);
        if (creator == null) {
            throw new IllegalArgumentException("Unknown llm.provider '" + provider + "', known: " + creators.keySet());
        }
        if (!"mock".equals(provider)) {
            checkLocal(config.get("llm.baseUrl", "http://localhost"));
        }
        String model = modelOverride == null || modelOverride.isBlank()
                ? config.get("llm.model", "qwen2.5:3b") : modelOverride;
        return creator.create(config, model);
    }

    public LlmOptions defaultOptions() {
        return new LlmOptions(config.getDouble("llm.temperature", 0.3), config.getInt("llm.maxTokens", 600), false);
    }

    private void checkLocal(String url) {
        if (config.getBool("llm.allowRemote", false)) {
            return;
        }
        String host = URI.create(url).getHost();
        if (host == null) {
            throw new IllegalArgumentException("Invalid llm.baseUrl: " + url);
        }
        boolean local = Set.of("localhost", "127.0.0.1", "::1", "[::1]").contains(host)
                || host.startsWith("192.168.") || host.startsWith("10.") || host.matches("172\\.(1[6-9]|2\\d|3[01])\\..*")
                || host.endsWith(".local");
        if (!local) {
            throw new IllegalArgumentException("llm.baseUrl points to a non-local host (" + host
                    + "). Emails are confidential: set llm.allowRemote=true only if you really trust this server.");
        }
    }
}
