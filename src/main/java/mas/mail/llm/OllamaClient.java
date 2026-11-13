package mas.mail.llm;

import mas.mail.util.Json;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** adapter for the Ollama API ({@code POST /api/chat}) */
public class OllamaClient extends HttpLlmClient {

    private final int contextWindow;
    private final String think;

    /**
     * @param think "false" turns off the reasoning step of thinking models (qwen3, deepseek-r1...),
     *              "true" turns it on, empty = model default
     */
    public OllamaClient(String baseUrl, String model, Duration timeout, int contextWindow, String think) {
        super(baseUrl, model, timeout);
        this.contextWindow = contextWindow;
        this.think = think == null ? "" : think.trim();
    }

    @Override
    protected String chatPath() {
        return "/api/chat";
    }

    @Override
    protected Map<String, Object> buildPayload(List<ChatMessage> messages, LlmOptions options) {
        Map<String, Object> opts = new LinkedHashMap<>();
        opts.put("temperature", options.temperature());
        opts.put("num_predict", options.maxTokens());
        opts.put("num_ctx", contextWindow);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", model);
        payload.put("messages", toApiMessages(messages));
        payload.put("stream", false);
        payload.put("options", opts);
        if (!think.isEmpty()) {
            payload.put("think", Boolean.parseBoolean(think));
        }
        if (options.jsonOutput()) {
            payload.put("format", "json");
        }
        return payload;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected LlmResponse parseResponse(Map<String, Object> json, long durationMs) {
        if (json.containsKey("error")) {
            throw new LlmException("Ollama error: " + json.get("error"));
        }
        Map<String, Object> message = (Map<String, Object>) json.get("message");
        String text = message == null ? "" : String.valueOf(message.get("content"));
        // older engines put the reasoning of thinking models inside the content
        text = text.replaceAll("(?s)<think>.*?</think>", "").strip();
        return new LlmResponse(text, model, asInt(json.get("prompt_eval_count")), asInt(json.get("eval_count")),
                durationMs);
    }

    @Override
    @SuppressWarnings("unchecked")
    public boolean isAvailable() {
        try {
            Map<String, Object> tags = Json.parseObject(get("/api/tags"));
            List<Object> models = (List<Object>) tags.getOrDefault("models", List.of());
            for (Object m : models) {
                String name = String.valueOf(((Map<String, Object>) m).get("name"));
                if (name.equals(model) || name.equals(model + ":latest")) {
                    return true;
                }
            }
            return false;
        } catch (RuntimeException e) {
            return false;
        }
    }
}
