package mas.mail.agent;

import mas.mail.comm.AclMessage;
import mas.mail.comm.MessageBus;

/**
 * an agent of the organisation
 * it has an id, a role and its own knowledge
 * it talks to other agents only by sending {@link AclMessage}s through the {@link MessageBus}
 */
public interface Agent {

    /** unique id, used as the address on the bus */
    String id();

    /** name shown to the user */
    String displayName();

    /** role of the agent in the organisation */
    AgentRole role();

    /** called by the bus when the agent joins the organisation */
    void attach(MessageBus bus);

    /**
     * handles a message and returns the answer
     * returns null when no answer is needed
     */
    AclMessage handle(AclMessage message);
}
