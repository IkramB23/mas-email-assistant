package mas.mail.routing;

import mas.mail.agent.protocol.CompetenceQuery;
import mas.mail.config.AgentProfile;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * uses the Contract Net protocol: the router sends a call for proposals and each specialist makes a bid
 * each specialist knows best what it can handle
 * the router does not need to know their rules, so adding an agent does not change the router
 * <p>
 * a refusal counts as a vote of 0, not as "no vote"
 * "nobody fits" is useful information for the vote
 * without it, another strategy alone would decide for emails that fit no agent
 */
public final class ContractNetStrategy implements RoutingStrategy {

    @Override
    public String name() {
        return "contract-net";
    }

    @Override
    public List<AgentScore> score(RoutingContext ctx) {
        Map<String, AgentScore> bids = new HashMap<>();
        for (AgentScore bid : ctx.callForProposals().apply(new CompetenceQuery(ctx.email(), ctx.sender()))) {
            bids.put(bid.agentId(), bid);
        }
        List<AgentScore> out = new ArrayList<>();
        for (AgentProfile p : ctx.candidates()) {
            out.add(bids.getOrDefault(p.id(), new AgentScore(p.id(), 0, List.of("refused the call for proposals"))));
        }
        return out;
    }
}
