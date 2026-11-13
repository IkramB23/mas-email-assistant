package mas.mail.agent;

/** the roles of the organisation */
public enum AgentRole {
    /** gets the emails, picks the specialist, runs the process and talks to the user */
    COORDINATOR,
    /** writes replies for one kind of email, with its own prompt and context */
    SPECIALIST,
    /** checks the drafts before the user sees them */
    REVIEWER,
    /** remembers the conversation threads */
    MEMORY
}
