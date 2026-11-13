package mas.mail.routing;

import mas.mail.config.AgentProfile;
import mas.mail.model.Contact;
import mas.mail.model.Email;

/** how a specialist judges if it is good for an email (used to answer a CFP) */
public interface CompetenceEvaluator {

    AgentScore evaluate(AgentProfile profile, Email email, Contact sender);
}
