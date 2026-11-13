package mas.mail.mail;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.UUID;

/**
 * writes accepted replies as .eml files in an outbox folder
 * safe by default: nothing leaves the computer
 */
public final class OutboxDirectorySender implements MailSender {

    private final Path dir;

    public OutboxDirectorySender(Path dir) {
        this.dir = dir;
    }

    @Override
    public String send(OutgoingEmail e) {
        String id = UUID.randomUUID().toString().substring(0, 12) + "@mail-assistant.local";
        StringBuilder sb = new StringBuilder();
        sb.append("Message-ID: <").append(id).append(">\n");
        sb.append("Date: ").append(ZonedDateTime.now().format(DateTimeFormatter.RFC_1123_DATE_TIME)).append('\n');
        sb.append("From: ").append(e.from()).append('\n');
        sb.append("To: ").append(String.join(", ", e.to())).append('\n');
        sb.append("Subject: ").append(e.subject()).append('\n');
        if (!e.inReplyTo().isBlank()) {
            sb.append("In-Reply-To: <").append(e.inReplyTo()).append(">\n");
            sb.append("References: <").append(e.threadId()).append(">")
                    .append(e.threadId().equals(e.inReplyTo()) ? "" : " <" + e.inReplyTo() + ">").append('\n');
        }
        sb.append("X-Drafted-By: ").append(e.draftedBy()).append('\n');
        sb.append('\n').append(e.body()).append('\n');
        try {
            Files.createDirectories(dir);
            String file = ZonedDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss", Locale.ROOT))
                    + "-" + id.substring(0, 6) + ".eml";
            Files.writeString(dir.resolve(file), sb.toString(), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
        return id;
    }
}
