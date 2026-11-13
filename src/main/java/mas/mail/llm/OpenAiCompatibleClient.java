package mas.mail.llm;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * adapter for local servers that use the OpenAI chat API ({@code POST /v1/chat/completions})
 * for example LM Studio, llama.cpp server, vLLM, LocalAI, Jan...
 */
public class OpenAiCompatibleClient extends HttpLlmClient {

    public OpenAiCompatibleClient(String baseUrl, String model, Duration timeout) {
        super(baseUrl, model, timeout);
    }

    @Override
    protected String chatPath() {
        return "/v1/chat/completions";
    }

    @Override
    protected Map<String, Object> buildPayload(List<ChatMessage> messages, LlmOptions options) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", model);
        payload.put("messages", toApiMessages(messages));
        payload.put("temperature", options.temperature());
        payload.put("max_tokens", options.maxTokens());
        payload.put("stream", false);
        if (options.jsonOutput()) {
            payload.put("response_format", Map.of("type", "json_object"));
        }
        return payload;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected LlmResponse parseResponse(Map<String, Object> json, long durationMs) {
        List<Object> choices = (List<Object>) json.get("choices");
        if (choices == null || choices.isEmpty()) {
            throw new LlmException("No choices in response: " + json);
        }
        Map<String, Object> message = (Map<String, Object>) ((Map<String, Object>) choices.get(0)).get("message");
        Map<String, Object> usage = (Map<String, Object>) json.getOrDefault("usage", Map.of());
        return new LlmResponse(String.valueOf(message.get("content")), model,
                asInt(usage.get("prompt_tokens")), asInt(usage.get("completion_tokens")), durationMs);
    }

    @Override
    public boolean isAvailable() {
        try {
            get("/v1/models");
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }
}
