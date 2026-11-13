package mas.mail.agent;

import mas.mail.comm.AclMessage;
import mas.mail.comm.MessageBus;
import mas.mail.comm.Performative;

/**
 * base class for all agents
 * it sends each incoming message to the right method, based on its performative
 * it also has helpers to talk to other agents
 * subclasses only override the messages they care about
 */
public abstract class AbstractAgent implements Agent {

    private final String id;
    private final String displayName;
    private MessageBus bus;

    protected AbstractAgent(String id, String displayName) {
        this.id = id;
        this.displayName = displayName;
    }

    @Override
    public final String id() {
        return id;
    }

    @Override
    public String displayName() {
        return displayName;
    }

    @Override
    public final void attach(MessageBus messageBus) {
        this.bus = messageBus;
    }

    @Override
    public final AclMessage handle(AclMessage m) {
        try {
            return switch (m.performative()) {
                case REQUEST -> onRequest(m);
                case CFP -> onCallForProposal(m);
                case INFORM -> onInform(m);
                default -> null;
            };
        } catch (RuntimeException e) {
            return m.reply(Performative.FAILURE, id + " failed: " + e.getMessage());
        }
    }

    protected AclMessage onRequest(AclMessage m) {
        return m.reply(Performative.NOT_UNDERSTOOD, id + " does not handle requests of this kind");
    }

    protected AclMessage onCallForProposal(AclMessage m) {
        return m.reply(Performative.REFUSE, id + " does not take part in calls for proposals");
    }

    protected AclMessage onInform(AclMessage m) {
        return null;
    }

    protected MessageBus bus() {
        if (bus == null) {
            throw new IllegalStateException("Agent " + id + " is not attached to a message bus");
        }
        return bus;
    }

    /**
     * sends a REQUEST and returns the answer
     * throws an error if the other agent failed
     */
    protected AclMessage request(String receiver, String conversationId, Object content) {
        AclMessage answer = bus().send(AclMessage.of(Performative.REQUEST, id, receiver, conversationId, content));
        if (answer == null) {
            throw new IllegalStateException("No answer from " + receiver);
        }
        if (answer.is(Performative.FAILURE) || answer.is(Performative.NOT_UNDERSTOOD)
                || answer.is(Performative.REFUSE)) {
            throw new AgentException(String.valueOf(answer.content()));
        }
        return answer;
    }

    /** sends an INFORM and does not wait for an answer */
    protected void inform(String receiver, String conversationId, Object content) {
        bus().send(AclMessage.of(Performative.INFORM, id, receiver, conversationId, content));
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "[" + id + "]";
    }
}
