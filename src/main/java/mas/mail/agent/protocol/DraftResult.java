package mas.mail.agent.protocol;

import mas.mail.model.Contact;
import mas.mail.model.Email;
import mas.mail.routing.RoutingDecision;

/**
 * what the router shows to the user: the routing decision, the proposal and its review
 *
 * @param conversationId id shared by all agent messages for this email
 * @param email          email answered
 * @param sender         information about the sender
 * @param routing        routing decision (with scores and explanation)
 * @param proposal       proposed reply
 * @param review         report of the reviewer
 * @param attempt        1 for the first proposal, +1 each time it is generated again
 * @param options        knowledge options used
 */
public record DraftResult(String conversationId, Email email, Contact sender, RoutingDecision routing,
                          DraftProposal proposal, ReviewReport review, int attempt, DraftOptions options) {
}
