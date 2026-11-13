package mas.mail.routing;

import mas.mail.agent.protocol.CompetenceQuery;
import mas.mail.config.AgentProfile;
import mas.mail.model.Contact;
import mas.mail.model.Email;
import mas.mail.model.ThreadEntry;

import java.util.List;
import java.util.function.Function;

/**
 * everything a routing strategy can use
 * strategies never see the message bus
 * the router gives them a function to send the call for proposals
 *
 * @param email            email to route
 * @param sender           what we know about the sender
 * @param thread           raw events of the thread (old messages and drafts)
 * @param candidates       profiles of the specialists that can be chosen (not the fallback)
 * @param callForProposals sends a CFP to the specialists and returns their bids
 */
public record RoutingContext(Email email, Contact sender, List<ThreadEntry> thread, List<AgentProfile> candidates,
                             Function<CompetenceQuery, List<AgentScore>> callForProposals) {
}
