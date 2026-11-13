package mas.mail.model;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * an email message that cannot be changed
 *
 * @param id        Message-ID (unique)
 * @param from      sender address (lowercase)
 * @param fromName  sender name (can be empty)
 * @param to        recipients
 * @param cc        recipients in copy
 * @param subject   subject line
 * @param body      plain text body
 * @param date      date it was sent
 * @param threadId  id of the conversation thread (Message-ID of the first email of the thread)
 * @param inReplyTo Message-ID of the parent email, or empty
 * @param headers   all headers, keys in lowercase
 */
public record Email(String id, String from, String fromName, List<String> to, List<String> cc,
                    String subject, String body, LocalDateTime date, String threadId, String inReplyTo,
                    Map<String, String> headers) {

    public Email {
        to = List.copyOf(to);
        cc = List.copyOf(cc);
        headers = Map.copyOf(headers);
        from = from == null ? "" : from.toLowerCase(Locale.ROOT);
        fromName = fromName == null ? "" : fromName;
        subject = subject == null ? "" : subject;
        body = body == null ? "" : body;
        inReplyTo = inReplyTo == null ? "" : inReplyTo;
        threadId = threadId == null || threadId.isBlank() ? id : threadId;
    }

    public String header(String name) {
        return headers.getOrDefault(name.toLowerCase(Locale.ROOT), "");
    }

    public String senderDomain() {
        int at = from.indexOf('@');
        return at < 0 ? "" : from.substring(at + 1);
    }

    public String displaySender() {
        return fromName.isBlank() ? from : fromName + " <" + from + ">";
    }

    public boolean isReply() {
        return !inReplyTo.isBlank();
    }

    @Override
    public String toString() {
        return "Email[" + id + " from " + from + " '" + subject + "']";
    }
}
