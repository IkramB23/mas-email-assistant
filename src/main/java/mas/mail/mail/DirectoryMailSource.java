package mas.mail.mail;

import mas.mail.model.Email;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/** reads every {@code .eml} file of a folder, sorted by date */
public final class DirectoryMailSource implements MailSource {

    private final Path dir;

    public DirectoryMailSource(Path dir) {
        this.dir = dir;
    }

    @Override
    public List<Email> fetch() {
        List<Email> out = new ArrayList<>();
        if (!Files.isDirectory(dir)) {
            return out;
        }
        try (Stream<Path> files = Files.list(dir)) {
            for (Path f : files.filter(p -> p.toString().endsWith(".eml")).sorted().toList()) {
                String name = f.getFileName().toString();
                out.add(EmailParser.parse(Files.readString(f, StandardCharsets.UTF_8),
                        name.substring(0, name.length() - 4)));
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        out.sort(Comparator.comparing(Email::date));
        return out;
    }
}
