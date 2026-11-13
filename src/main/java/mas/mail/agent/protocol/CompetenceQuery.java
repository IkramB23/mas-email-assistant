package mas.mail.agent.protocol;

import mas.mail.model.Contact;
import mas.mail.model.Email;

/** content of a call for proposals: "how good are you to answer this email?" */
public record CompetenceQuery(Email email, Contact sender) {
}
