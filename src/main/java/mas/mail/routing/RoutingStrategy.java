package mas.mail.routing;

import java.util.List;

/**
 * strategy that scores how well each agent fits an email
 * an empty list means the strategy does not vote (no opinion, for example the LLM is down
 * or the thread has no history)
 * strategies that do not vote do not lower the scores of the others
 */
public interface RoutingStrategy {

    String name();

    List<AgentScore> score(RoutingContext context);
}
