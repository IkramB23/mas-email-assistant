package mas.mail.app;

import mas.mail.agent.Agent;
import mas.mail.agent.AgentFactory;
import mas.mail.agent.AgentRole;
import mas.mail.agent.MemoryAgent;
import mas.mail.agent.ReviewerAgent;
import mas.mail.agent.RouterAgent;
import mas.mail.agent.protocol.DraftOptions;
import mas.mail.agent.protocol.DraftResult;
import mas.mail.agent.protocol.MemoryRecord;
import mas.mail.agent.protocol.ProcessEmail;
import mas.mail.agent.protocol.Protocol;
import mas.mail.agent.protocol.Regenerate;
import mas.mail.comm.AclMessage;
import mas.mail.comm.MessageBus;
import mas.mail.comm.MessageListener;
import mas.mail.comm.Performative;
import mas.mail.config.AgentProfile;
import mas.mail.config.AgentProfileRepository;
import mas.mail.config.AppConfig;
import mas.mail.config.ContactDirectory;
import mas.mail.config.FileAgentProfileRepository;
import mas.mail.config.UserProfile;
import mas.mail.llm.LlmClient;
import mas.mail.llm.LlmClientFactory;
import mas.mail.mail.DirectoryMailSource;
import mas.mail.mail.MailSender;
import mas.mail.mail.MailSource;
import mas.mail.mail.OutboxDirectorySender;
import mas.mail.mail.OutgoingEmail;
import mas.mail.memory.ConversationRepository;
import mas.mail.memory.FileConversationRepository;
import mas.mail.memory.HistoryWindowStrategy;
import mas.mail.memory.LastNWindow;
import mas.mail.memory.TokenBudgetWindow;
import mas.mail.model.Email;
import mas.mail.model.ThreadEntry;
import mas.mail.routing.DecisionPolicy;
import mas.mail.routing.RoutingDecision;
import mas.mail.routing.RoutingStrategyFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * main entry of the framework (facade)
 * it builds the organisation from the config (composition root)
 * it gives the few actions the interfaces need (GUI, CLI, experiments)
 * the user talks to the organisation by sending messages to the router as the "user" agent
 */
public final class MailAssistant {

    private final AppConfig config;
    private final UserProfile user;
    private final ContactDirectory contacts;
    private final AgentProfileRepository profileRepository;
    private final ConversationRepository conversations;
    private final MailSource inbox;
    private final MailSender sender;
    private final MessageBus bus = new MessageBus();
    private final LlmClientFactory llmFactory;
    private final AgentFactory agentFactory;
    private final LlmClient defaultLlm;
    private final RoutingStrategyFactory strategyFactory;
    private final RouterAgent router;
    private volatile List<AgentProfile> profiles = List.of();

    public MailAssistant(AppConfig config) {
        this.config = config;
        Path data = config.dataDir();
        this.user = UserProfile.load(data.resolve("user"));
        this.contacts = ContactDirectory.load(data.resolve("contacts.csv"));
        this.profileRepository = new FileAgentProfileRepository(data.resolve("agents"));
        this.conversations = new FileConversationRepository(config.path("paths.memory", "data/memory/threads"));
        this.inbox = new DirectoryMailSource(data.resolve("mail").resolve("inbox"));
        this.sender = new OutboxDirectorySender(data.resolve("mail").resolve("outbox"));
        this.llmFactory = new LlmClientFactory(config);
        this.agentFactory = new AgentFactory(llmFactory, user);
        this.defaultLlm = llmFactory.create();
        this.strategyFactory = new RoutingStrategyFactory(defaultLlm, llmFactory.defaultOptions());

        importHistory(data.resolve("mail").resolve("history"));

        bus.addListener(new TraceLogger(config.path("paths.log", "logs/trace.log")));
        bus.register(new MemoryAgent(Protocol.MEMORY, conversations, historyWindow()));
        if (config.getBool("review.enabled", true)) {
            bus.register(new ReviewerAgent(Protocol.REVIEWER, user.signature()));
        }
        DecisionPolicy policy = new DecisionPolicy(config.getDouble("routing.threshold", 0.35),
                config.getDouble("routing.ambiguityMargin", 0.10), config.get("routing.fallbackAgent", "generic"));
        this.router = new RouterAgent(Protocol.ROUTER, contacts, () -> profiles, user,
                strategyFactory.create(config.get("routing.strategies", "rules:1.0,llm:1.0,thread:0.5")), policy,
                config.getBool("review.enabled", true), config.getBool("review.autoRevise", true));
        bus.register(router);
        reloadAgents();
    }

    // ------------------------------------------------------------------ processing

    public List<Email> inbox() {
        return inbox.fetch();
    }

    public DraftResult process(Email email) {
        return process(email, null, DraftOptions.FULL);
    }

    public DraftResult process(Email email, String forcedAgentId, DraftOptions options) {
        return askRouter(new ProcessEmail(email, forcedAgentId, options));
    }

    /**
     * rejects a proposal and asks for a new one
     * uses the same agent if agentId is null
     */
    public DraftResult regenerate(DraftResult previous, String feedback, String agentId) {
        return askRouter(new Regenerate(previous, feedback, agentId));
    }

    /** only routing, no draft */
    public RoutingDecision previewRouting(Email email) {
        return router.route(email, newConversationId());
    }

    /**
     * the user accepted the reply (maybe after editing it)
     * it is sent and saved in memory
     */
    public String accept(DraftResult result, String finalText) {
        Email e = result.email();
        String subject = e.subject().regionMatches(true, 0, "Re:", 0, 3) ? e.subject() : "Re: " + e.subject();
        String id = sender.send(new OutgoingEmail(user.address(), List.of(e.from()), subject, finalText, e.id(),
                e.threadId(), result.proposal().agentId()));
        boolean edited = !finalText.strip().equals(result.proposal().text().strip());
        ThreadEntry sent = new ThreadEntry(ThreadEntry.Kind.SENT, id, user.address(), LocalDateTime.now(), subject,
                finalText, result.proposal().agentId(), "in-reply-to:" + e.id() + (edited ? " (edited by user)" : ""));
        bus.send(AclMessage.of(Performative.INFORM, Protocol.USER, Protocol.MEMORY, result.conversationId(),
                new MemoryRecord(e.threadId(), sent)));
        return id;
    }

    /** the user throws away the proposal and does not want a new one */
    public void discard(DraftResult result, String reason) {
        Email e = result.email();
        bus.send(AclMessage.of(Performative.INFORM, Protocol.USER, Protocol.MEMORY, result.conversationId(),
                new MemoryRecord(e.threadId(), new ThreadEntry(ThreadEntry.Kind.REJECTED, e.id(), Protocol.USER,
                        LocalDateTime.now(), e.subject(), result.proposal().text(), result.proposal().agentId(),
                        reason))));
    }

    public boolean isReplied(Email e) {
        return conversations.thread(e.threadId()).stream()
                .anyMatch(t -> t.kind() == ThreadEntry.Kind.SENT && t.note().contains("in-reply-to:" + e.id()));
    }

    private DraftResult askRouter(Object content) {
        AclMessage answer = bus.send(AclMessage.of(Performative.REQUEST, Protocol.USER, Protocol.ROUTER,
                newConversationId(), content));
        if (answer != null && answer.is(Performative.INFORM) && answer.content() instanceof DraftResult r) {
            return r;
        }
        throw new IllegalStateException(answer == null ? "No answer from the router" : String.valueOf(answer.content()));
    }

    private static String newConversationId() {
        return UUID.randomUUID().toString().substring(0, 6);
    }

    // ------------------------------------------------------------------ organisation management

    /**
     * builds the specialists again from their profiles
     * so agents can be added or changed while the program runs
     */
    public synchronized void reloadAgents() {
        for (Agent a : bus.agents()) {
            if (a.role() == AgentRole.SPECIALIST) {
                bus.unregister(a.id());
            }
        }
        List<AgentProfile> loaded = new ArrayList<>();
        for (AgentProfile p : profileRepository.findAll()) {
            if (p.enabled()) {
                bus.register(agentFactory.create(p));
            }
            loaded.add(p);
        }
        profiles = List.copyOf(loaded);
    }

    public List<AgentProfile> profiles() {
        return profiles;
    }

    public AgentProfile saveProfile(AgentProfile p) {
        AgentProfile saved = profileRepository.save(p);
        reloadAgents();
        return saved;
    }

    public void deleteProfile(String id) {
        profileRepository.delete(id);
        reloadAgents();
    }

    /** copies a document into the private context folder of an agent */
    public Path addContextFile(String agentId, Path file, boolean pinned) {
        AgentProfile p = profileRepository.findById(agentId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown agent " + agentId));
        Path dir = pinned ? p.contextDir().resolve("pinned") : p.contextDir();
        try {
            Files.createDirectories(dir);
            return Files.copy(file, dir.resolve(file.getFileName()), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public void setRoutingStrategy(String spec) {
        router.setStrategy(strategyFactory.create(spec));
    }

    public String routingStrategyName() {
        return router.strategy().name();
    }

    public void addListener(MessageListener l) {
        bus.addListener(l);
    }

    public List<Agent> agents() {
        return List.copyOf(bus.agents());
    }

    public LlmClient defaultLlm() {
        return defaultLlm;
    }

    public AppConfig config() {
        return config;
    }

    public UserProfile user() {
        return user;
    }

    public ConversationRepository conversations() {
        return conversations;
    }

    // ------------------------------------------------------------------ setup helpers

    private HistoryWindowStrategy historyWindow() {
        return "lastN".equalsIgnoreCase(config.get("memory.window", "budget"))
                ? new LastNWindow(config.getInt("memory.lastN", 6))
                : new TokenBudgetWindow(config.getInt("memory.maxCharsPerMessage", 1200));
    }

    /** loads old emails (received and sent) into memory, only once */
    private void importHistory(Path dir) {
        for (Email e : new DirectoryMailSource(dir).fetch()) {
            boolean sentByUser = e.from().equalsIgnoreCase(user.address());
            ThreadEntry entry = sentByUser
                    ? new ThreadEntry(ThreadEntry.Kind.SENT, e.id(), e.from(), e.date(), e.subject(), e.body(),
                    e.header("x-drafted-by"), "in-reply-to:" + e.inReplyTo())
                    : ThreadEntry.received(e);
            conversations.append(e.threadId(), entry);
        }
    }
}
