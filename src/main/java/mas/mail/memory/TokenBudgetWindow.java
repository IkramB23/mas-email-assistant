package mas.mail.memory;

import mas.mail.model.ThreadEntry;
import mas.mail.util.Text;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * keeps the most recent emails that fit in the token budget (received emails and replies really sent)
 * drafts that were not accepted are ignored, only what really happened matters
 * long emails are cut so one old email cannot use the whole budget
 */
public final class TokenBudgetWindow implements HistoryWindowStrategy {

    private final int maxCharsPerEntry;

    public TokenBudgetWindow(int maxCharsPerEntry) {
        this.maxCharsPerEntry = maxCharsPerEntry;
    }

    @Override
    public List<ThreadEntry> select(List<ThreadEntry> thread, String currentMessageId, int tokenBudget) {
        List<ThreadEntry> out = new ArrayList<>();
        int used = 0;
        for (int i = thread.size() - 1; i >= 0; i--) {
            ThreadEntry e = thread.get(i);
            boolean real = e.kind() == ThreadEntry.Kind.RECEIVED || e.kind() == ThreadEntry.Kind.SENT;
            if (!real || e.messageId().equals(currentMessageId)) {
                continue;
            }
            String text = Text.truncate(e.text(), maxCharsPerEntry);
            int cost = Text.estimateTokens(text) + 15;
            if (used + cost > tokenBudget) {
                break;
            }
            used += cost;
            out.add(new ThreadEntry(e.kind(), e.messageId(), e.author(), e.timestamp(), e.subject(), text,
                    e.agentId(), e.note()));
        }
        Collections.reverse(out);
        return out;
    }
}
