package mas.mail.agent;

import mas.mail.agent.protocol.CompetenceQuery;
import mas.mail.agent.protocol.DraftProposal;
import mas.mail.agent.protocol.DraftRequest;
import mas.mail.agent.protocol.HistoryAnswer;
import mas.mail.agent.protocol.HistoryQuery;
import mas.mail.agent.protocol.Protocol;
import mas.mail.comm.AclMessage;
import mas.mail.comm.Performative;
import mas.mail.config.AgentProfile;
import mas.mail.context.ContextChunk;
import mas.mail.context.ContextRetriever;
import mas.mail.llm.ChatMessage;
import mas.mail.llm.LlmClient;
import mas.mail.llm.LlmOptions;
import mas.mail.llm.LlmResponse;
import mas.mail.routing.AgentScore;
import mas.mail.routing.CompetenceEvaluator;
import mas.mail.util.Text;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * a specialist agent
 * what it is good at is fully described by its {@link AgentProfile}
 * (instructions, routing hints, budgets) and by its own private context
 * <p>
 * it helps the organisation in two ways:
 * - it answers calls for proposals by saying how good it is for an email (Contract Net)
 * - it writes replies with a fixed list of steps (Template Method {@link #draft})
 *   subclasses can change each step
 */
public class SpecializedAgent extends AbstractAgent {

    private final AgentProfile profile;
    private final LlmClient llm;
    private final LlmOptions options;
    private final ContextRetriever retriever;
    private final CompetenceEvaluator competence;
    private final PromptBuilder promptBuilder;

    public SpecializedAgent(AgentProfile profile, LlmClient llm, LlmOptions options, ContextRetriever retriever,
                            CompetenceEvaluator competence, PromptBuilder promptBuilder) {
        super(profile.id(), profile.displayName());
        this.profile = profile;
        this.llm = llm;
        this.options = options;
        this.retriever = retriever;
        this.competence = competence;
        this.promptBuilder = promptBuilder;
    }

    @Override
    public AgentRole role() {
        return AgentRole.SPECIALIST;
    }

    public AgentProfile profile() {
        return profile;
    }

    @Override
    protected AclMessage onCallForProposal(AclMessage m) {
        CompetenceQuery q = m.contentAs(CompetenceQuery.class);
        if (profile.fallback()) {
            return m.reply(Performative.REFUSE, "fallback agent: only used when no specialist fits");
        }
        AgentScore bid = competence.evaluate(profile, q.email(), q.sender());
        return bid.score() > 0 ? m.reply(Performative.PROPOSE, bid)
                : m.reply(Performative.REFUSE, "no evidence of competence");
    }

    @Override
    protected AclMessage onRequest(AclMessage m) {
        if (m.content() instanceof DraftRequest r) {
            return m.reply(Performative.INFORM, draft(r, m.conversationId()));
        }
        return super.onRequest(m);
    }

    /** uses the Template Method pattern: the list of steps is fixed, but each step can be overridden */
    public final DraftProposal draft(DraftRequest r, String conversationId) {
        long start = System.currentTimeMillis();
        HistoryAnswer history = r.options().useHistory() ? retrieveHistory(r, conversationId) : HistoryAnswer.EMPTY;
        List<ContextChunk> context = r.options().useContext() ? retrieveContext(r) : List.of();
        List<ChatMessage> prompt = buildPrompt(r, context, history);
        LlmResponse response = llm.chat(prompt, options);
        String text = postProcess(response.text());
        String promptText = render(prompt);
        int promptTokens = response.promptTokens() > 0 ? response.promptTokens() : Text.estimateTokens(promptText);
        Set<String> sources = new LinkedHashSet<>();
        context.forEach(c -> sources.add(c.source()));
        return new DraftProposal(id(), displayName(), text, List.copyOf(sources), history.size(), promptTokens,
                llm.modelName(), System.currentTimeMillis() - start, promptText);
    }

    /** step 1: asks the memory agent for the useful part of the conversation */
    protected HistoryAnswer retrieveHistory(DraftRequest r, String conversationId) {
        try {
            HistoryQuery q = new HistoryQuery(r.email().threadId(), r.email().id(), r.email().from(),
                    profile.maxHistoryTokens(), false);
            return request(Protocol.MEMORY, conversationId, q).contentAs(HistoryAnswer.class);
        } catch (RuntimeException e) {
            return HistoryAnswer.EMPTY; // memory not available, continue without it
        }
    }

    /**
     * step 2: picks the useful documents from this agent's private context
     * it also removes the information that must never be shared
     * the LLM cannot leak what it never sees (privacy by design)
     * the reviewer is still there as a second check
     */
    protected List<ContextChunk> retrieveContext(DraftRequest r) {
        String query = r.email().subject() + "\n" + r.email().body() + (r.isRevision() ? "\n" + r.feedback() : "");
        Set<String> forbidden = promptBuilder.forbiddenTerms(profile);
        return retriever.retrieve(query, profile.maxContextTokens()).stream()
                .map(c -> new ContextChunk(c.source(), redact(c.text(), forbidden), c.pinned()))
                .toList();
    }

    static String redact(String text, Set<String> forbidden) {
        String out = text;
        for (String term : forbidden) {
            if (!term.isBlank()) {
                out = out.replaceAll("(?i)" + java.util.regex.Pattern.quote(term), "[CONFIDENTIAL]");
            }
        }
        return out;
    }

    /** step 3: builds the prompt */
    protected List<ChatMessage> buildPrompt(DraftRequest r, List<ContextChunk> context, HistoryAnswer history) {
        return promptBuilder.build(profile, r, context, history);
    }

    /** step 4: cleans the raw text from the LLM */
    protected String postProcess(String raw) {
        String t = raw == null ? "" : raw.replaceAll("(?s)<think>.*?</think>", "").strip();
        t = t.replaceAll("(?s)^```[a-zA-Z]*\\n(.*)\\n```$", "$1").strip();
        t = t.replaceFirst("(?i)^(subject|objet)\\s*:.*\\n+", "");
        t = t.replaceFirst("(?i)^(here is|voici)[^\\n]*:\\s*\\n+", "");
        return withSignature(t.strip(), promptBuilder.signature());
    }

    /**
     * small models often forget, repeat, translate or misplace the signature
     * so the code handles it, not the model
     * short lines made mostly of signature words are removed
     * then the real signature is added once at the end
     */
    static String withSignature(String text, String signature) {
        java.util.Set<String> sigWords = new java.util.HashSet<>(words(signature));
        StringBuilder out = new StringBuilder();
        for (String line : text.split("\n", -1)) {
            List<String> w = words(line);
            long inSig = w.stream().filter(sigWords::contains).count();
            boolean signatureLine = !w.isEmpty() && w.size() <= 8 && line.length() < 80 && inSig >= 0.75 * w.size();
            if (!signatureLine) {
                out.append(line).append('\n');
            }
        }
        return out.toString().strip().replaceAll("\n{3,}", "\n\n") + "\n" + signature.strip();
    }

    private static List<String> words(String s) {
        return java.util.Arrays.stream(Text.normalize(s).split("[^\\p{L}\\p{N}]+")).filter(x -> !x.isBlank()).toList();
    }

    private static String render(List<ChatMessage> prompt) {
        StringBuilder sb = new StringBuilder();
        for (ChatMessage m : prompt) {
            sb.append("===== ").append(m.role()).append(" =====\n").append(m.content()).append("\n\n");
        }
        return sb.toString();
    }
}
