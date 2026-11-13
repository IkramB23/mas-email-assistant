package mas.mail.agent.protocol;

/**
 * the user rejected a draft and wants a new one
 *
 * @param previous last result
 * @param feedback user comments to use
 * @param agentId  agent to use (null = same agent, another id = the user picks another agent)
 */
public record Regenerate(DraftResult previous, String feedback, String agentId) {
}
