package mas.mail.routing;

import java.util.List;

/**
 * how well an agent fits an email (a bid in the Contract Net, or a vote of a strategy)
 *
 * @param agentId agent
 * @param score   between 0 and 1
 * @param reasons short reasons for the score (to explain the routing)
 */
public record AgentScore(String agentId, double score, List<String> reasons) {

    public AgentScore {
        score = Math.max(0, Math.min(1, score));
        reasons = List.copyOf(reasons);
    }
}
