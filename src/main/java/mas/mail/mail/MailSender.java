package mas.mail.mail;

/**
 * sends a reply that the user accepted
 * it is only called after the user clearly says yes
 * the given version writes to an outbox folder, an SMTP version can be added
 */
public interface MailSender {

    /** sends the email and returns its Message-ID */
    String send(OutgoingEmail email);
}
