package mas.mail.comm;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * a message sent between agents (Agent Communication Language)
 *
 * @param id             unique id
 * @param performative   type of message (what the sender wants)
 * @param sender         id of the agent that sends it
 * @param receiver       id of the agent that gets it
 * @param conversationId same for all messages about one email
 * @param inReplyTo      id of the message this one answers, or null
 * @param content        payload (an object from {@code mas.mail.agent.protocol})
 * @param timestamp      time of creation
 */
public record AclMessage(String id, Performative performative, String sender, String receiver,
                         String conversationId, String inReplyTo, Object content, LocalDateTime timestamp) {

    public static AclMessage of(Performative p, String sender, String receiver, String conversationId,
                                Object content) {
        return new AclMessage(newId(), p, sender, receiver, conversationId, null, content, LocalDateTime.now());
    }

    /**
     * builds an answer to this message
     * sender and receiver are swapped, the conversation stays the same
     */
    public AclMessage reply(Performative p, Object replyContent) {
        return new AclMessage(newId(), p, receiver, sender, conversationId, id, replyContent, LocalDateTime.now());
    }

    /** same message sent to another agent (used to send a CFP to all) */
    public AclMessage withReceiver(String newReceiver) {
        return new AclMessage(newId(), performative, sender, newReceiver, conversationId, inReplyTo, content,
                timestamp);
    }

    /**
     * gets the content with the right type
     * throws an error if the type is wrong
     */
    public <T> T contentAs(Class<T> type) {
        if (!type.isInstance(content)) {
            throw new IllegalStateException("Expected content " + type.getSimpleName() + " but got "
                    + (content == null ? "null" : content.getClass().getSimpleName()));
        }
        return type.cast(content);
    }

    public boolean is(Performative p) {
        return performative == p;
    }

    private static String newId() {
        return UUID.randomUUID().toString().substring(0, 8);
    }
}
