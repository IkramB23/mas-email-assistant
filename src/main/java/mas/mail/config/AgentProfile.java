package mas.mail.config;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * description of a specialist agent
 * it is saved as simple files
 * ({@code data/agents/<id>/agent.properties}, {@code system_prompt.md}, {@code context/})
 * so the user can create or change agents without touching the code
 *
 * @param id               unique id (also the folder name)
 * @param displayName      name shown to the user
 * @param description      what the agent is in charge of (also read by the LLM router)
 * @param type             which class to use, chosen by the AgentFactory ("specialized" by default)
 * @param enabled          disabled agents are not in the organisation
 * @param fallback         true for the agent used when no other agent fits
 * @param keywords         keywords with weights that describe its emails
 * @param senderPatterns   address patterns of its usual senders (glob, like {@code *@etu.univ-example.fr})
 * @param senderRoles      contact roles it handles (student, lecturer...)
 * @param senderTags       contact tags it handles (like a project name)
 * @param language         reply language: "auto" (same as the email), "en", "fr"...
 * @param maxContextTokens max size of the context sent to the LLM
 * @param maxHistoryTokens max size of the conversation history sent to the LLM
 * @param maxWords         max length of a reply (checked by the reviewer)
 * @param forbiddenTerms   information that must never be in a reply
 * @param modelOverride    model to use instead of the global one (optional)
 * @param systemPrompt     the agent's own instructions
 * @param directory        folder of the agent (has its private context)
 */
public record AgentProfile(String id, String displayName, String description, String type, boolean enabled,
                           boolean fallback, Map<String, Double> keywords, List<String> senderPatterns,
                           List<String> senderRoles, List<String> senderTags, String language,
                           int maxContextTokens, int maxHistoryTokens, int maxWords, List<String> forbiddenTerms,
                           String modelOverride, String systemPrompt, Path directory) {

    public AgentProfile {
        keywords = Map.copyOf(keywords);
        senderPatterns = List.copyOf(senderPatterns);
        senderRoles = List.copyOf(senderRoles);
        senderTags = List.copyOf(senderTags);
        forbiddenTerms = List.copyOf(forbiddenTerms);
        modelOverride = modelOverride == null ? "" : modelOverride;
        systemPrompt = systemPrompt == null ? "" : systemPrompt;
        type = type == null || type.isBlank() ? "specialized" : type;
    }

    /** folder with the private context documents of the agent */
    public Path contextDir() {
        return directory.resolve("context");
    }

    public AgentProfile withSystemPrompt(String prompt) {
        return new AgentProfile(id, displayName, description, type, enabled, fallback, keywords, senderPatterns,
                senderRoles, senderTags, language, maxContextTokens, maxHistoryTokens, maxWords, forbiddenTerms,
                modelOverride, prompt, directory);
    }

    public AgentProfile withDirectory(Path dir) {
        return new AgentProfile(id, displayName, description, type, enabled, fallback, keywords, senderPatterns,
                senderRoles, senderTags, language, maxContextTokens, maxHistoryTokens, maxWords, forbiddenTerms,
                modelOverride, systemPrompt, dir);
    }
}
