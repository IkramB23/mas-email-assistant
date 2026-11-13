package mas.mail.mail;

import mas.mail.model.Email;

import java.util.List;

/**
 * where the received emails come from
 * the given version reads a folder of .eml files
 * an IMAP version (for example with Jakarta Mail) can be added without changing the agents
 */
public interface MailSource {

    List<Email> fetch();
}
