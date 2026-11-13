package mas.mail.memory;

import mas.mail.model.ThreadEntry;

import java.util.List;

/** strategy that chooses which part of a conversation to send to the LLM */
public interface HistoryWindowStrategy {

    /**
     * @param thread           entries of the thread, oldest first
     * @param currentMessageId message being answered (not included, it is sent on its own)
     * @param tokenBudget      max size of the chosen history
     * @return chosen entries, oldest first
     */
    List<ThreadEntry> select(List<ThreadEntry> thread, String currentMessageId, int tokenBudget);
}
