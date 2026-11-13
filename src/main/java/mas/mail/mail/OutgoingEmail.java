package mas.mail.mail;

import java.util.List;

/** a reply accepted by the user, ready to send */
public record OutgoingEmail(String from, List<String> to, String subject, String body, String inReplyTo,
                            String threadId, String draftedBy) {

    public OutgoingEmail {
        to = List.copyOf(to);
    }
}
