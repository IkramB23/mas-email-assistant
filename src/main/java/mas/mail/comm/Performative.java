package mas.mail.comm;

/**
 * types of messages, based on the FIPA-ACL standard
 * they give a clear meaning to each message between agents
 */
public enum Performative {
    /** asks the other agent to do something (for example write a reply) */
    REQUEST,
    /** call for proposals: asks the other agent if it can do a task, and how well (Contract Net) */
    CFP,
    /** an offer sent as an answer to a CFP */
    PROPOSE,
    /** the agent says no to a CFP or a request */
    REFUSE,
    /** gives some information (result of an action, fact to remember...) */
    INFORM,
    /** the agent tried to do the action but failed */
    FAILURE,
    /** the agent does not understand the content of the message */
    NOT_UNDERSTOOD
}
