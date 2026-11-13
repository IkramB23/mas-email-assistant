package mas.mail.agent.protocol;

import java.util.List;

/**
 * a draft written by a specialist
 *
 * @param agentId        agent that wrote it
 * @param agentName      name of that agent
 * @param text           text of the proposed reply
 * @param contextSources documents used as context
 * @param historyEntries number of old messages given to the LLM
 * @param promptTokens   size of the prompt
 * @param model          model used
 * @param durationMs     time to generate the draft
 * @param prompt         full prompt sent to the LLM (to see and debug it)
 */
public record DraftProposal(String agentId, String agentName, String text, List<String> contextSources,
                            int historyEntries, int promptTokens, String model, long durationMs, String prompt) {

    public DraftProposal {
        contextSources = List.copyOf(contextSources);
    }
}
