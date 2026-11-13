package mas.mail.llm;

/**
 * generation settings, the same for every engine
 *
 * @param temperature randomness of the output
 * @param maxTokens   max number of tokens to generate
 * @param jsonOutput  ask the engine to answer only in JSON, if it can
 */
public record LlmOptions(double temperature, int maxTokens, boolean jsonOutput) {

    public LlmOptions withJsonOutput(boolean json) {
        return new LlmOptions(temperature, maxTokens, json);
    }

    public LlmOptions withTemperature(double t) {
        return new LlmOptions(t, maxTokens, jsonOutput);
    }

    public LlmOptions withMaxTokens(int n) {
        return new LlmOptions(temperature, n, jsonOutput);
    }
}
