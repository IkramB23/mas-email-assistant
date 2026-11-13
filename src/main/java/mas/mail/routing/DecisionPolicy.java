package mas.mail.routing;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * turns a ranking into a decision:
 * - best score below {@code threshold}: no agent fits well enough, the fallback agent is used
 * - best and second scores closer than {@code margin}: ambiguous, the best one writes the draft
 *   but the user is warned and can pick the other one
 * - otherwise: confident decision
 */
public final class DecisionPolicy {

    private final double threshold;
    private final double margin;
    private final String fallbackAgentId;

    public DecisionPolicy(double threshold, double margin, String fallbackAgentId) {
        this.threshold = threshold;
        this.margin = margin;
        this.fallbackAgentId = fallbackAgentId;
    }

    public RoutingDecision decide(List<AgentScore> scores) {
        List<AgentScore> ranking = new ArrayList<>(scores);
        ranking.sort(Comparator.comparingDouble(AgentScore::score).reversed());
        if (ranking.isEmpty() || ranking.get(0).score() < threshold) {
            double best = ranking.isEmpty() ? 0 : ranking.get(0).score();
            return new RoutingDecision(fallbackAgentId, best, RoutingDecision.Status.FALLBACK, ranking,
                    String.format(Locale.ROOT, "No specialist reached the threshold %.2f (best %.2f): "
                            + "fallback agent '%s' is used.", threshold, best, fallbackAgentId));
        }
        AgentScore best = ranking.get(0);
        String why = String.join(" | ", best.reasons());
        if (ranking.size() > 1 && best.score() - ranking.get(1).score() < margin) {
            AgentScore second = ranking.get(1);
            return new RoutingDecision(best.agentId(), best.score(), RoutingDecision.Status.AMBIGUOUS, ranking,
                    String.format(Locale.ROOT, "Ambiguous: '%s' (%.2f) and '%s' (%.2f) are both suitable. "
                                    + "'%s' was selected; you may re-route to '%s'. Reasons: %s",
                            best.agentId(), best.score(), second.agentId(), second.score(), best.agentId(),
                            second.agentId(), why));
        }
        return new RoutingDecision(best.agentId(), best.score(), RoutingDecision.Status.CONFIDENT, ranking,
                String.format(Locale.ROOT, "'%s' selected with score %.2f. Reasons: %s", best.agentId(),
                        best.score(), why));
    }

    public String fallbackAgentId() {
        return fallbackAgentId;
    }
}
