package mas.mail.config;

import mas.mail.util.Text;

import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;

/**
 * the person who uses the assistant: name, signature and preferences shared by all agents
 * read from {@code data/user/user.properties} and {@code data/user/preferences.md}
 */
public record UserProfile(String name, String title, String address, String signature, String preferences,
                          List<String> forbiddenTerms) {

    public UserProfile {
        forbiddenTerms = List.copyOf(forbiddenTerms);
    }

    public static UserProfile load(Path userDir) {
        Properties p = new Properties();
        Path props = userDir.resolve("user.properties");
        Path prefs = userDir.resolve("preferences.md");
        try {
            if (Files.exists(props)) {
                try (Reader r = Files.newBufferedReader(props, StandardCharsets.UTF_8)) {
                    p.load(r);
                }
            }
            String preferences = Files.exists(prefs) ? Files.readString(prefs, StandardCharsets.UTF_8) : "";
            return new UserProfile(
                    p.getProperty("name", "User"),
                    p.getProperty("title", ""),
                    p.getProperty("address", "user@localhost"),
                    p.getProperty("signature", p.getProperty("name", "User")).replace("\\n", "\n"),
                    preferences,
                    Text.splitList(p.getProperty("forbiddenTerms", "")));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
