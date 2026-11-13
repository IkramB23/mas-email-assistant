package mas.mail.llm;

import mas.mail.util.Json;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * base class for local LLM engines used over HTTP (Template Method pattern)
 * the HTTP code, timing and error handling are shared here
 * subclasses only build the request and read the answer of their engine
 */
public abstract class HttpLlmClient implements LlmClient {

    protected final String baseUrl;
    protected final String model;
    private final Duration timeout;
    private final HttpClient http;

    protected HttpLlmClient(String baseUrl, String model, Duration timeout) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.model = model;
        this.timeout = timeout;
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    }

    @Override
    public final LlmResponse chat(List<ChatMessage> messages, LlmOptions options) {
        long start = System.currentTimeMillis();
        String body = Json.stringify(buildPayload(messages, options));
        String answer = post(chatPath(), body);
        Map<String, Object> json;
        try {
            json = Json.parseObject(answer);
        } catch (RuntimeException e) {
            throw new LlmException("Invalid JSON from inference engine: " + answer, e);
        }
        return parseResponse(json, System.currentTimeMillis() - start);
    }

    @Override
    public String modelName() {
        return model;
    }

    /** path of the chat endpoint, after the base URL */
    protected abstract String chatPath();

    /** request body for this engine */
    protected abstract Map<String, Object> buildPayload(List<ChatMessage> messages, LlmOptions options);

    /** reads the answer of this engine */
    protected abstract LlmResponse parseResponse(Map<String, Object> json, long durationMs);

    protected static List<Map<String, Object>> toApiMessages(List<ChatMessage> messages) {
        List<Map<String, Object>> list = new ArrayList<>();
        for (ChatMessage m : messages) {
            list.add(Map.of("role", m.role().apiName(), "content", m.content()));
        }
        return list;
    }

    protected String post(String path, String jsonBody) {
        HttpRequest req = HttpRequest.newBuilder(URI.create(baseUrl + path))
                .timeout(timeout)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();
        return execute(req);
    }

    protected String get(String path) {
        HttpRequest req = HttpRequest.newBuilder(URI.create(baseUrl + path))
                .timeout(Duration.ofSeconds(5)).GET().build();
        return execute(req);
    }

    private String execute(HttpRequest req) {
        try {
            HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() / 100 != 2) {
                throw new LlmException("HTTP " + res.statusCode() + " from " + req.uri() + ": " + res.body());
            }
            return res.body();
        } catch (IOException e) {
            throw new LlmException("Cannot reach the local inference engine at " + baseUrl
                    + " (is it running?): " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new LlmException("Interrupted while waiting for the LLM", e);
        }
    }

    protected static int asInt(Object o) {
        return o instanceof Number n ? n.intValue() : 0;
    }
}
