package mas.mail.agent;

import mas.mail.config.AgentProfile;
import mas.mail.config.UserProfile;
import mas.mail.context.Bm25Scorer;
import mas.mail.context.ContextRetriever;
import mas.mail.context.DirectoryContextSource;
import mas.mail.llm.LlmClient;
import mas.mail.llm.LlmClientFactory;
import mas.mail.routing.ProfileCompetenceEvaluator;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * creates the specialist agents from their profiles
 * the {@code type} value of a profile chooses which class to use
 * to add a new kind of agent, just register a new creator
 * the rest of the code does not change
 */
public final class AgentFactory {

    /** builds an agent of one type */
    @FunctionalInterface
    public interface Creator {
        Agent create(AgentProfile profile, AgentFactory factory);
    }

    private final Map<String, Creator> creators = new LinkedHashMap<>();
    private final Map<String, LlmClient> clientsByModel = new HashMap<>();
    private final LlmClientFactory llmFactory;
    private final PromptBuilder promptBuilder;

    public AgentFactory(LlmClientFactory llmFactory, UserProfile user) {
        this.llmFactory = llmFactory;
        this.promptBuilder = new PromptBuilder(user);
        register("specialized", AgentFactory::defaultSpecialist);
    }

    public void register(String type, Creator creator) {
        creators.put(type, creator);
    }

    public Agent create(AgentProfile profile) {
        Creator c = creators.get(profile.type());
        if (c == null) {
            throw new IllegalArgumentException("Unknown agent type '" + profile.type() + "' for agent " + profile.id()
                    + ", known: " + creators.keySet());
        }
        return c.create(profile, this);
    }

    /**
     * gives the LLM client for a profile
     * a profile can use its own model
     * agents with the same model share one client
     */
    public LlmClient llmFor(AgentProfile profile) {
        return clientsByModel.computeIfAbsent(profile.modelOverride(), llmFactory::create);
    }

    public LlmClientFactory llmFactory() {
        return llmFactory;
    }

    public PromptBuilder promptBuilder() {
        return promptBuilder;
    }

    private static Agent defaultSpecialist(AgentProfile p, AgentFactory f) {
        ContextRetriever retriever = new ContextRetriever(List.of(new DirectoryContextSource(p.contextDir())),
                new Bm25Scorer(), 0.5);
        return new SpecializedAgent(p, f.llmFor(p), f.llmFactory.defaultOptions(), retriever,
                new ProfileCompetenceEvaluator(), f.promptBuilder);
    }
}
