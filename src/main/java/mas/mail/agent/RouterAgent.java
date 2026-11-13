package mas.mail.agent;

import mas.mail.agent.protocol.CompetenceQuery;
import mas.mail.agent.protocol.DraftOptions;
import mas.mail.agent.protocol.DraftProposal;
import mas.mail.agent.protocol.DraftRequest;
import mas.mail.agent.protocol.DraftResult;
import mas.mail.agent.protocol.HistoryAnswer;
import mas.mail.agent.protocol.HistoryQuery;
import mas.mail.agent.protocol.MemoryRecord;
import mas.mail.agent.protocol.ProcessEmail;
import mas.mail.agent.protocol.Protocol;
import mas.mail.agent.protocol.Regenerate;
import mas.mail.agent.protocol.ReviewReport;
import mas.mail.agent.protocol.ReviewRequest;
import mas.mail.comm.AclMessage;
import mas.mail.comm.Performative;
import mas.mail.config.AgentProfile;
import mas.mail.config.ContactDirectory;
import mas.mail.config.UserProfile;
import mas.mail.model.Contact;
import mas.mail.model.Email;
import mas.mail.model.ThreadEntry;
import mas.mail.routing.AgentScore;
import mas.mail.routing.DecisionPolicy;
import mas.mail.routing.RoutingContext;
import mas.mail.routing.RoutingDecision;
import mas.mail.routing.RoutingStrategy;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

/**
 * the coordinator of the organisation (one central dispatcher)
 * for each email it does these steps:
 * - saves the email in memory and gets its thread
 * - routes it: the strategies (Contract Net bids, LLM classification, thread continuity) vote,
 *   then the {@link DecisionPolicy} decides (confident, ambiguous or fallback)
 * - asks the chosen specialist to write the draft
 * - asks the reviewer to check the draft, and asks for one fix if needed
 * - gives the proposal to the user, who always makes the final choice
 */
public final class RouterAgent extends AbstractAgent {

    private final ContactDirectory contacts;
    private final Supplier<List<AgentProfile>> profiles;
    private final UserProfile user;
    private volatile RoutingStrategy strategy;
    private final DecisionPolicy policy;
    private final boolean reviewEnabled;
    private final boolean autoRevise;

    public RouterAgent(String id, ContactDirectory contacts, Supplier<List<AgentProfile>> profiles, UserProfile user,
                       RoutingStrategy strategy, DecisionPolicy policy, boolean reviewEnabled, boolean autoRevise) {
        super(id, "Router");
        this.contacts = contacts;
        this.profiles = profiles;
        this.user = user;
        this.strategy = strategy;
        this.policy = policy;
        this.reviewEnabled = reviewEnabled;
        this.autoRevise = autoRevise;
    }

    @Override
    public AgentRole role() {
        return AgentRole.COORDINATOR;
    }

    public RoutingStrategy strategy() {
        return strategy;
    }

    /** changes the routing strategy while the program runs (used by the experiments) */
    public void setStrategy(RoutingStrategy s) {
        this.strategy = s;
    }

    @Override
    protected AclMessage onRequest(AclMessage m) {
        Object c = m.content();
        if (c instanceof ProcessEmail p) {
            return m.reply(Performative.INFORM, process(p, m.conversationId()));
        }
        if (c instanceof Regenerate g) {
            return m.reply(Performative.INFORM, regenerate(g, m.conversationId()));
        }
        return super.onRequest(m);
    }

    /**
     * only does the routing, no draft
     * used by the GUI preview and the routing experiments
     */
    public RoutingDecision route(Email email, String conversationId) {
        Contact sender = contacts.lookup(email.from(), email.fromName());
        return route(email, sender, fetchThread(email, conversationId), conversationId);
    }

    private DraftResult process(ProcessEmail p, String conv) {
        Email email = p.email();
        Contact sender = contacts.lookup(email.from(), email.fromName());
        inform(Protocol.MEMORY, conv, new MemoryRecord(email.threadId(), ThreadEntry.received(email)));
        RoutingDecision decision;
        if (p.forcedAgentId() != null) {
            decision = RoutingDecision.manual(p.forcedAgentId(), List.of());
        } else {
            decision = route(email, sender, fetchThread(email, conv), conv);
        }
        return draftWith(conv, email, sender, decision, null, null, 1, p.options());
    }

    private DraftResult regenerate(Regenerate g, String conv) {
        DraftResult prev = g.previous();
        Email email = prev.email();
        inform(Protocol.MEMORY, conv, new MemoryRecord(email.threadId(), new ThreadEntry(ThreadEntry.Kind.REJECTED,
                email.id(), Protocol.USER, LocalDateTime.now(), email.subject(), prev.proposal().text(),
                prev.proposal().agentId(), g.feedback())));
        RoutingDecision decision = prev.routing();
        boolean rerouted = g.agentId() != null && !g.agentId().equals(prev.proposal().agentId());
        if (rerouted) {
            decision = RoutingDecision.manual(g.agentId(), prev.routing().ranking());
        }
        String feedback = g.feedback() == null || g.feedback().isBlank()
                ? "The user rejected the previous draft without comment: propose a different formulation."
                : g.feedback();
        // a new agent starts from zero but still gets the user's comments
        return draftWith(conv, email, prev.sender(), decision, feedback, rerouted ? null : prev.proposal().text(),
                prev.attempt() + 1, prev.options());
    }

    private List<ThreadEntry> fetchThread(Email email, String conv) {
        try {
            HistoryQuery q = new HistoryQuery(email.threadId(), email.id(), email.from(), Integer.MAX_VALUE, true);
            return request(Protocol.MEMORY, conv, q).contentAs(HistoryAnswer.class).thread();
        } catch (RuntimeException e) {
            return List.of();
        }
    }

    private RoutingDecision route(Email email, Contact sender, List<ThreadEntry> thread, String conv) {
        Set<String> present = new LinkedHashSet<>();
        bus().agents().forEach(a -> present.add(a.id()));
        List<AgentProfile> candidates = profiles.get().stream()
                .filter(p -> p.enabled() && !p.fallback() && present.contains(p.id()))
                .toList();
        RoutingContext ctx = new RoutingContext(email, sender, thread, candidates, q -> callForProposals(q, conv));
        return policy.decide(strategy.score(ctx));
    }

    private List<AgentScore> callForProposals(CompetenceQuery q, String conv) {
        AclMessage cfp = AclMessage.of(Performative.CFP, id(), "*", conv, q);
        List<AgentScore> bids = new ArrayList<>();
        for (AclMessage answer : bus().broadcast(cfp, a -> a.role() == AgentRole.SPECIALIST)) {
            if (answer.is(Performative.PROPOSE) && answer.content() instanceof AgentScore s) {
                bids.add(s);
            }
        }
        return bids;
    }

    private DraftResult draftWith(String conv, Email email, Contact sender, RoutingDecision decision, String feedback,
                                  String previousDraft, int attempt, DraftOptions options) {
        String agentId = decision.selectedAgentId();
        AgentProfile profile = profiles.get().stream().filter(p -> p.id().equals(agentId)).findFirst()
                .orElseThrow(() -> new AgentException("Unknown agent '" + agentId + "'"));
        DraftRequest req = new DraftRequest(email, sender, feedback, previousDraft, options);
        DraftProposal proposal = request(agentId, conv, req).contentAs(DraftProposal.class);
        ReviewReport review = review(conv, email, proposal, profile);
        if (!review.ok() && autoRevise) {
            String fix = "An automatic check found problems, fix them: " + String.join(" ", review.issues())
                    + (feedback == null ? "" : "\nUser feedback: " + feedback);
            DraftProposal revised = request(agentId, conv, new DraftRequest(email, sender, fix, proposal.text(), options))
                    .contentAs(DraftProposal.class);
            ReviewReport revisedReview = review(conv, email, revised, profile);
            // keep the fixed draft unless it is worse than the first one
            // small models sometimes make it worse
            if (revisedReview.issues().size() <= review.issues().size()) {
                proposal = revised;
                review = revisedReview;
            }
        }
        inform(Protocol.MEMORY, conv, new MemoryRecord(email.threadId(), new ThreadEntry(ThreadEntry.Kind.PROPOSED,
                email.id(), id(), LocalDateTime.now(), email.subject(), proposal.text(), agentId, "")));
        return new DraftResult(conv, email, sender, decision, proposal, review, attempt, options);
    }

    private ReviewReport review(String conv, Email email, DraftProposal proposal, AgentProfile profile) {
        if (!reviewEnabled || bus().lookup(Protocol.REVIEWER).isEmpty()) {
            return ReviewReport.NOT_REVIEWED;
        }
        List<String> forbidden = new ArrayList<>(profile.forbiddenTerms());
        forbidden.addAll(user.forbiddenTerms());
        ReviewRequest rr = new ReviewRequest(email, proposal, forbidden, profile.maxWords(), profile.language());
        return request(Protocol.REVIEWER, conv, rr).contentAs(ReviewReport.class);
    }
}
