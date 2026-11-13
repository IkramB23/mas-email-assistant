package mas.mail.config;

import mas.mail.util.Text;

import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.stream.Stream;

/**
 * saves each agent in its own folder:
 * <pre>
 * data/agents/&lt;id&gt;/agent.properties   settings (routing hints, budgets...)
 * data/agents/&lt;id&gt;/system_prompt.md    the agent's instructions
 * data/agents/&lt;id&gt;/context/            private documents (context/pinned/ = always sent)
 * </pre>
 */
public final class FileAgentProfileRepository implements AgentProfileRepository {

    private static final String PROPS = "agent.properties";
    private static final String PROMPT = "system_prompt.md";

    private final Path root;

    public FileAgentProfileRepository(Path root) {
        this.root = root;
    }

    @Override
    public List<AgentProfile> findAll() {
        List<AgentProfile> out = new ArrayList<>();
        if (!Files.isDirectory(root)) {
            return out;
        }
        try (Stream<Path> dirs = Files.list(root)) {
            dirs.filter(d -> Files.exists(d.resolve(PROPS)))
                    .sorted(Comparator.comparing(Path::getFileName))
                    .forEach(d -> out.add(load(d)));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return out;
    }

    @Override
    public Optional<AgentProfile> findById(String id) {
        Path dir = root.resolve(id);
        return Files.exists(dir.resolve(PROPS)) ? Optional.of(load(dir)) : Optional.empty();
    }

    @Override
    public AgentProfile save(AgentProfile p) {
        if (!p.id().matches("[a-zA-Z0-9_-]+")) {
            throw new IllegalArgumentException("Agent id must only contain letters, digits, '-' or '_'");
        }
        Path dir = root.resolve(p.id());
        try {
            Files.createDirectories(dir.resolve("context").resolve("pinned"));
            Properties props = new Properties();
            props.setProperty("id", p.id());
            props.setProperty("name", p.displayName());
            props.setProperty("description", p.description());
            props.setProperty("type", p.type());
            props.setProperty("enabled", String.valueOf(p.enabled()));
            props.setProperty("fallback", String.valueOf(p.fallback()));
            props.setProperty("keywords", formatKeywords(p.keywords()));
            props.setProperty("senderPatterns", String.join(", ", p.senderPatterns()));
            props.setProperty("senderRoles", String.join(", ", p.senderRoles()));
            props.setProperty("senderTags", String.join(", ", p.senderTags()));
            props.setProperty("language", p.language());
            props.setProperty("maxContextTokens", String.valueOf(p.maxContextTokens()));
            props.setProperty("maxHistoryTokens", String.valueOf(p.maxHistoryTokens()));
            props.setProperty("maxWords", String.valueOf(p.maxWords()));
            props.setProperty("forbiddenTerms", String.join(", ", p.forbiddenTerms()));
            props.setProperty("llm.model", p.modelOverride());
            try (Writer w = Files.newBufferedWriter(dir.resolve(PROPS), StandardCharsets.UTF_8)) {
                props.store(w, "Agent profile - editable by hand or from the GUI");
            }
            Files.writeString(dir.resolve(PROMPT), p.systemPrompt(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return p.withDirectory(dir);
    }

    @Override
    public void delete(String id) {
        Path dir = root.resolve(id);
        if (!Files.exists(dir)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(dir)) {
            for (Path p : walk.sorted(Comparator.reverseOrder()).toList()) {
                Files.delete(p);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private AgentProfile load(Path dir) {
        Properties p = new Properties();
        try (Reader r = Files.newBufferedReader(dir.resolve(PROPS), StandardCharsets.UTF_8)) {
            p.load(r);
            Path promptFile = dir.resolve(PROMPT);
            String prompt = Files.exists(promptFile) ? Files.readString(promptFile, StandardCharsets.UTF_8) : "";
            String id = p.getProperty("id", dir.getFileName().toString()).trim();
            return new AgentProfile(
                    id,
                    p.getProperty("name", id).trim(),
                    p.getProperty("description", "").trim(),
                    p.getProperty("type", "specialized").trim(),
                    Boolean.parseBoolean(p.getProperty("enabled", "true").trim()),
                    Boolean.parseBoolean(p.getProperty("fallback", "false").trim()),
                    parseKeywords(p.getProperty("keywords", "")),
                    Text.splitList(p.getProperty("senderPatterns", "")),
                    lower(Text.splitList(p.getProperty("senderRoles", ""))),
                    lower(Text.splitList(p.getProperty("senderTags", ""))),
                    p.getProperty("language", "auto").trim(),
                    intProp(p, "maxContextTokens", 900),
                    intProp(p, "maxHistoryTokens", 700),
                    intProp(p, "maxWords", 250),
                    Text.splitList(p.getProperty("forbiddenTerms", "")),
                    p.getProperty("llm.model", "").trim(),
                    prompt,
                    dir);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** reads "exam:1.5, course, grade:2" into a map of keyword -> weight */
    public static Map<String, Double> parseKeywords(String s) {
        Map<String, Double> out = new LinkedHashMap<>();
        for (String item : Text.splitList(s)) {
            int colon = item.lastIndexOf(':');
            double w = 1.0;
            String kw = item;
            if (colon > 0) {
                try {
                    w = Double.parseDouble(item.substring(colon + 1).trim());
                    kw = item.substring(0, colon);
                } catch (NumberFormatException ignored) {
                    // here the colon is part of the keyword
                }
            }
            String norm = Text.normalize(kw).trim();
            if (!norm.isEmpty()) {
                out.put(norm, w);
            }
        }
        return out;
    }

    public static String formatKeywords(Map<String, Double> kw) {
        List<String> parts = new ArrayList<>();
        kw.forEach((k, w) -> parts.add(w == 1.0 ? k : k + ":" + w));
        return String.join(", ", parts);
    }

    private static List<String> lower(List<String> l) {
        return l.stream().map(Text::normalize).toList();
    }

    private static int intProp(Properties p, String key, int def) {
        try {
            return Integer.parseInt(p.getProperty(key, String.valueOf(def)).trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }
}
