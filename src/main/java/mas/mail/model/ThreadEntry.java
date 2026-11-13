package mas.mail.model;

import java.time.LocalDateTime;

/**
 * one event in the memory of a conversation thread
 *
 * @param kind      type of event
 * @param messageId id of the related message (used to avoid duplicates)
 * @param author    address of the author ("user" side or the other person)
 * @param timestamp when it happened
 * @param subject   subject of the message
 * @param text      content
 * @param agentId   agent that wrote the draft (for PROPOSED, REJECTED, SENT), or empty
 * @param note      free note (like the user comments when a draft is rejected)
 */
public record ThreadEntry(Kind kind, String messageId, String author, LocalDateTime timestamp,
                          String subject, String text, String agentId, String note) {

    /** kinds of events saved in a thread */
    public enum Kind {
        /** email received by the user */
        RECEIVED,
        /** draft written by an agent, not accepted yet */
        PROPOSED,
        /** draft rejected by the user (comments are in {@code note}) */
        REJECTED,
        /** reply really sent by the user (maybe edited) */
        SENT
    }

    public ThreadEntry {
        agentId = agentId == null ? "" : agentId;
        note = note == null ? "" : note;
        subject = subject == null ? "" : subject;
        text = text == null ? "" : text;
    }

    public static ThreadEntry received(Email e) {
        return new ThreadEntry(Kind.RECEIVED, e.id(), e.from(), e.date(), e.subject(), e.body(), "", "");
    }
}
