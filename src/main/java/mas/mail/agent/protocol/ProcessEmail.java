package mas.mail.agent.protocol;

import mas.mail.model.Email;

/**
 * asks the router to handle a new email
 *
 * @param email         the email
 * @param forcedAgentId if not null, skip routing and use this agent (chosen by the user)
 * @param options       generation options (the experiments use it to turn off context, prompt...)
 */
public record ProcessEmail(Email email, String forcedAgentId, DraftOptions options) {

    public ProcessEmail(Email email) {
        this(email, null, DraftOptions.FULL);
    }
}
