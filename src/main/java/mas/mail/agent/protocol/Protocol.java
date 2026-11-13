package mas.mail.agent.protocol;

/**
 * contents of the messages sent between agents
 * each record is the payload of an {@link mas.mail.comm.AclMessage}
 * with the performative, it defines how agents talk to each other:
 *
 * <pre>
 * user      --REQUEST(ProcessEmail)--------&gt; router
 * router    --INFORM(MemoryRecord)---------&gt; memory
 * router    --REQUEST(HistoryQuery)--------&gt; memory        --INFORM(HistoryAnswer)--&gt; router
 * router    --CFP(CompetenceQuery)---------&gt; specialists   --PROPOSE(AgentScore) / REFUSE--&gt; router
 * router    --REQUEST(DraftRequest)--------&gt; specialist
 *   specialist --REQUEST(HistoryQuery)-----&gt; memory        --INFORM(HistoryAnswer)--&gt; specialist
 * specialist --INFORM(DraftProposal)-------&gt; router
 * router    --REQUEST(ReviewRequest)-------&gt; reviewer      --INFORM(ReviewReport)--&gt; router
 * router    --INFORM(DraftResult)----------&gt; user
 * user      --REQUEST(Regenerate)----------&gt; router  (rejected, with comments)
 * user      --INFORM(MemoryRecord SENT)----&gt; memory  (accepted)
 * </pre>
 */
public final class Protocol {
    private Protocol() {
    }

    /** fixed ids of the system agents */
    public static final String ROUTER = "router";
    public static final String MEMORY = "memory";
    public static final String REVIEWER = "reviewer";
    public static final String USER = "user";
}
