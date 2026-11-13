package mas.mail.agent.protocol;

import mas.mail.model.ThreadEntry;

/** asks the memory agent to save an event of a thread */
public record MemoryRecord(String threadId, ThreadEntry entry) {
}
