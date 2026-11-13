package mas.mail.agent.protocol;

import mas.mail.model.Contact;
import mas.mail.model.Email;

/**
 * asks a specialist to write a reply
 *
 * @param email         email to answer
 * @param sender        what we know about the sender
 * @param feedback      comments from the user or the reviewer on the last draft, or null for a first draft
 * @param previousDraft the rejected draft, or null
 * @param options       knowledge the agent can use
 */
public record DraftRequest(Email email, Contact sender, String feedback, String previousDraft,
                           DraftOptions options) {

    public boolean isRevision() {
        return feedback != null && !feedback.isBlank();
    }
}
