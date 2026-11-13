package mas.mail.memory;

import mas.mail.model.ThreadEntry;

import java.util.List;

/** keeps the last N real emails, whatever their size (simple baseline for experiments) */
public final class LastNWindow implements HistoryWindowStrategy {

    private final int n;

    public LastNWindow(int n) {
        this.n = n;
    }

    @Override
    public List<ThreadEntry> select(List<ThreadEntry> thread, String currentMessageId, int tokenBudget) {
        List<ThreadEntry> real = thread.stream()
                .filter(e -> e.kind() == ThreadEntry.Kind.RECEIVED || e.kind() == ThreadEntry.Kind.SENT)
                .filter(e -> !e.messageId().equals(currentMessageId))
                .toList();
        return real.subList(Math.max(0, real.size() - n), real.size());
    }
}
