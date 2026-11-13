package mas.mail.agent.protocol;

/**
 * asks the memory agent for the history useful for a message
 *
 * @param threadId         thread of the message
 * @param currentMessageId message being answered (not included in the history)
 * @param senderAddress    the other person (to find emails from other threads)
 * @param tokenBudget      max size of the history sent back
 * @param rawThread        true to get every event of the thread with no filter (used by the router)
 */
public record HistoryQuery(String threadId, String currentMessageId, String senderAddress, int tokenBudget,
                           boolean rawThread) {
}
