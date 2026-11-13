package mas.mail.routing;

import mas.mail.config.AgentProfile;
import mas.mail.llm.ChatMessage;
import mas.mail.llm.LlmClient;
import mas.mail.llm.LlmOptions;
import mas.mail.llm.LlmResponse;
import mas.mail.util.Json;
import mas.mail.util.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * asks the local LLM to give a score to each agent, based on its description
 * it understands meaning that keywords miss, but costs one more short LLM call
 * it does not vote if the LLM fails or gives an answer we cannot use
 */
public final class LlmClassifierStrategy implements RoutingStrategy {

    static final String NONE = "none";

    private final LlmClient llm;
    private final LlmOptions options;

    public LlmClassifierStrategy(LlmClient llm, LlmOptions baseOptions) {
        this.llm = llm;
        this.options = baseOptions.withJsonOutput(true).withTemperature(0.0).withMaxTokens(200);
    }

    @Override
    public String name() {
        return "llm";
    }

    @Override
    public List<AgentScore> score(RoutingContext ctx) {
        StringBuilder agents = new StringBuilder();
        for (AgentProfile p : ctx.candidates()) {
            agents.append("- \"").append(p.id()).append("\": ").append(p.description()).append('\n');
        }
        // there is a "none" option on purpose
        // without it, the model must pick an agent even for emails that fit no agent
        agents.append("- \"").append(NONE).append("\": the email fits none of the agents above ")
                .append("(unknown sender, unrelated invitation, solicitation...)\n");
        String system = "You are the dispatcher of an email assistant. You decide which specialised agent should "
                + "draft the reply to an email. Answer with JSON only.";
        String user = "Agents:\n" + agents
                + "\nEmail:\nSender: " + ctx.sender().describe()
                + "\nSubject: " + ctx.email().subject()
                + "\nBody:\n" + Text.truncate(ctx.email().body(), 1500)
                + "\n\nGive every agent a suitability score between 0 and 1 (several agents may be suitable, "
                + "all scores may be low if none fits). Format:\n"
                + "{\"scores\": {\"<agent id>\": <score>, ...}, \"reason\": \"<one short sentence>\"}";
        try {
            LlmResponse res = llm.chat(List.of(ChatMessage.system(system), ChatMessage.user(user)), options);
            return parse(res.text(), ctx);
        } catch (RuntimeException e) {
            return List.of();
        }
    }

    @SuppressWarnings("unchecked")
    private List<AgentScore> parse(String text, RoutingContext ctx) {
        Map<String, Object> json = Json.extractObject(text).orElse(Map.of());
        Object scoresObj = json.get("scores");
        if (!(scoresObj instanceof Map<?, ?>) || ((Map<?, ?>) scoresObj).isEmpty()) {
            return List.of();
        }
        Map<String, Object> scores = (Map<String, Object>) scoresObj;
        String reason = String.valueOf(json.getOrDefault("reason", ""));
        // the more the model thinks no agent fits, the less its votes for the agents count
        double none = value(scores.get(NONE));
        List<AgentScore> out = new ArrayList<>();
        for (AgentProfile p : ctx.candidates()) {
            double s = value(scores.get(p.id())) * (1 - none);
            out.add(new AgentScore(p.id(), s, s > 0.3 && !reason.isBlank() ? List.of(reason) : List.of()));
        }
        return out;
    }

    private static double value(Object v) {
        double s = v instanceof Number n ? n.doubleValue() : 0;
        if (s > 1) {
            s = s / 100.0; // some models answer in percent
        }
        return Math.max(0, Math.min(1, s));
    }
}
