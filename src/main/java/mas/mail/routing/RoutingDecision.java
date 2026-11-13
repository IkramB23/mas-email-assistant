package mas.mail.routing;

import java.util.List;

/**
 * result of the routing
 *
 * @param selectedAgentId chosen agent
 * @param confidence      score of the chosen agent
 * @param status          how the decision was made
 * @param ranking         all the candidates, best first
 * @param explanation     explanation for the user
 */
public record RoutingDecision(String selectedAgentId, double confidence, Status status, List<AgentScore> ranking,
                              String explanation) {

    /** kind of decision */
    public enum Status {
        /** one agent is clearly the best */
        CONFIDENT,
        /** some agents are close: the best one is used, but the user is warned and can pick another */
        AMBIGUOUS,
        /** no agent fits well enough: the fallback (generic) agent is used */
        FALLBACK,
        /** chosen by the user */
        MANUAL
    }

    public RoutingDecision {
        ranking = List.copyOf(ranking);
    }

    public static RoutingDecision manual(String agentId, List<AgentScore> previousRanking) {
        return new RoutingDecision(agentId, 1.0, Status.MANUAL, previousRanking, "Agent chosen manually by the user.");
    }
}
