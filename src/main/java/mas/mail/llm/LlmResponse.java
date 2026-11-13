package mas.mail.llm;

/**
 * result of a generation
 *
 * @param text             generated text
 * @param model            model that wrote it
 * @param promptTokens     tokens used by the prompt (given by the engine, or estimated)
 * @param completionTokens generated tokens
 * @param durationMs       real time it took
 */
public record LlmResponse(String text, String model, int promptTokens, int completionTokens, long durationMs) {
}
