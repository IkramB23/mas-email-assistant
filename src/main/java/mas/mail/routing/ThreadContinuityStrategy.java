package mas.mail.routing;

import mas.mail.model.ThreadEntry;

import java.util.List;

/**
 * if an agent already wrote a reply that was sent in this conversation,
 * it is probably the right one for the next email
 * does not vote when the thread has no such reply
 */
public final class ThreadContinuityStrategy implements RoutingStrategy {

    @Override
    public String name() {
        return "thread";
    }

    @Override
    public List<AgentScore> score(RoutingContext ctx) {
        List<ThreadEntry> thread = ctx.thread();
        for (int i = thread.size() - 1; i >= 0; i--) {
            ThreadEntry e = thread.get(i);
            if (e.kind() == ThreadEntry.Kind.SENT && !e.agentId().isBlank()
                    && ctx.candidates().stream().anyMatch(p -> p.id().equals(e.agentId()))) {
                return List.of(new AgentScore(e.agentId(), 1.0,
                        List.of("previous reply in this thread was drafted by " + e.agentId())));
            }
        }
        return List.of();
    }
}
