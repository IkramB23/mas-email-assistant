package mas.mail.app;

import mas.mail.comm.AclMessage;
import mas.mail.comm.MessageListener;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/**
 * observer that writes every agent message to a log file
 * this helps to check and explain the decisions
 */
public final class TraceLogger implements MessageListener {

    private final Path file;

    public TraceLogger(Path file) {
        this.file = file;
        try {
            Files.createDirectories(file.getParent());
        } catch (IOException ignored) {
            // logging must not break the program, errors are ignored
        }
    }

    @Override
    public synchronized void onMessage(AclMessage m) {
        try {
            Files.writeString(file, MessageFormatter.line(m) + System.lineSeparator(), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException ignored) {
            // logging must not break the program, errors are ignored
        }
    }
}
