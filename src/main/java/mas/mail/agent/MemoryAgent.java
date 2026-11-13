package mas.mail.agent;

import mas.mail.agent.protocol.HistoryAnswer;
import mas.mail.agent.protocol.HistoryQuery;
import mas.mail.agent.protocol.MemoryRecord;
import mas.mail.comm.AclMessage;
import mas.mail.comm.Performative;
import mas.mail.memory.ConversationRepository;
import mas.mail.memory.HistoryWindowStrategy;
import mas.mail.model.ThreadEntry;
import mas.mail.util.Text;

import java.util.ArrayList;
import java.util.List;

/**
 * remembers all the conversations
 * it is the only agent that can see the whole mailbox history
 * specialists only get the part they need for the email they answer
 * this keeps prompts small and keeps each agent's context separate
 */
public final class MemoryAgent extends AbstractAgent {

    /** part of the history budget kept for emails with the same sender in other threads */
    private static final double SENDER_SHARE = 0.3;

    private final ConversationRepository repository;
    private final HistoryWindowStrategy window;

    public MemoryAgent(String id, ConversationRepository repository, HistoryWindowStrategy window) {
        super(id, "Memory");
        this.repository = repository;
        this.window = window;
    }

    @Override
    public AgentRole role() {
        return AgentRole.MEMORY;
    }

    @Override
    protected AclMessage onRequest(AclMessage m) {
        HistoryQuery q = m.contentAs(HistoryQuery.class);
        List<ThreadEntry> thread = repository.thread(q.threadId());
        if (q.rawThread()) {
            return m.reply(Performative.INFORM, new HistoryAnswer(thread, List.of()));
        }
        int senderBudget = (int) (q.tokenBudget() * SENDER_SHARE);
        List<ThreadEntry> selected = window.select(thread, q.currentMessageId(), q.tokenBudget() - senderBudget);
        List<ThreadEntry> withSender = new ArrayList<>();
        int used = 0;
        for (ThreadEntry e : repository.recentWith(q.senderAddress(), q.threadId(), 4)) {
            String text = Text.truncate(e.text(), 400);
            int cost = Text.estimateTokens(text) + 15;
            if (used + cost > senderBudget) {
                break;
            }
            used += cost;
            withSender.add(0, new ThreadEntry(e.kind(), e.messageId(), e.author(), e.timestamp(), e.subject(), text,
                    e.agentId(), e.note()));
        }
        return m.reply(Performative.INFORM, new HistoryAnswer(selected, withSender));
    }

    @Override
    protected AclMessage onInform(AclMessage m) {
        if (m.content() instanceof MemoryRecord r) {
            repository.append(r.threadId(), r.entry());
        }
        return null;
    }
}
