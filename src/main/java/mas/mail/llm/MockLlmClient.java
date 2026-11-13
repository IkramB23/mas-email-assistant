package mas.mail.llm;

import mas.mail.util.Text;

import java.util.List;

/**
 * fake model that always gives the same answers
 * it lets the whole system run (GUI, routing, memory, tests) without any LLM engine
 * it also makes the routing tests repeatable
 */
public class MockLlmClient implements LlmClient {

    @Override
    public LlmResponse chat(List<ChatMessage> messages, LlmOptions options) {
        String prompt = messages.isEmpty() ? "" : messages.get(messages.size() - 1).content();
        String text;
        if (options.jsonOutput()) {
            // the mock does not vote in classification, so routing uses the other strategies
            text = "{\"scores\": {}, \"reason\": \"mock model does not classify\"}";
        } else {
            String subject = extractLine(prompt, "Subject:");
            String sender = extractLine(prompt, "From:");
            text = "Hello,\n\nThank you for your message" + (subject.isBlank() ? "" : " about \"" + subject + "\"")
                    + ".\nI have read it carefully and will get back to you with a precise answer shortly.\n\n"
                    + "[MOCK draft - configure a local LLM in config/app.properties to get real answers. "
                    + "Prompt size: ~" + Text.estimateTokens(prompt) + " tokens, sender: " + sender + "]\n\n"
                    + "Best regards";
        }
        return new LlmResponse(text, modelName(), Text.estimateTokens(prompt), Text.estimateTokens(text), 1);
    }

    private static String extractLine(String text, String prefix) {
        for (String line : text.split("\n")) {
            if (line.startsWith(prefix)) {
                return line.substring(prefix.length()).trim();
            }
        }
        return "";
    }

    @Override
    public String modelName() {
        return "mock";
    }

    @Override
    public boolean isAvailable() {
        return true;
    }
}
