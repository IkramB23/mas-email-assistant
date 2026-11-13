package mas.mail.llm;

import java.util.List;

/**
 * a local language model
 * the rest of the code only uses this interface
 * each engine has its own adapter (Ollama, OpenAI-compatible servers like LM Studio, llama.cpp, vLLM, or a mock for tests)
 */
public interface LlmClient {

    /** generates the answer for the given chat messages */
    LlmResponse chat(List<ChatMessage> messages, LlmOptions options) throws LlmException;

    /** name of the model used (for logs and experiments) */
    String modelName();

    /** quick check that the engine works and the model is there */
    boolean isAvailable();
}
