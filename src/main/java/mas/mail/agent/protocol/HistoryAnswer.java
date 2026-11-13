package mas.mail.agent.protocol;

import mas.mail.model.ThreadEntry;

import java.util.List;

/**
 * history sent back by the memory agent
 *
 * @param thread     chosen entries of the current thread (oldest first)
 * @param withSender recent emails with the same person in other threads
 */
public record HistoryAnswer(List<ThreadEntry> thread, List<ThreadEntry> withSender) {

    public static final HistoryAnswer EMPTY = new HistoryAnswer(List.of(), List.of());

    public HistoryAnswer {
        thread = List.copyOf(thread);
        withSender = List.copyOf(withSender);
    }

    public int size() {
        return thread.size() + withSender.size();
    }
}
