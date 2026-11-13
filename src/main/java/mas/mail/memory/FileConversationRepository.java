package mas.mail.memory;

import mas.mail.model.ThreadEntry;
import mas.mail.util.Json;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * one JSON file per thread in {@code data/memory/threads/}
 * simple, easy to read, and enough for a personal mailbox
 * another version (SQLite...) can replace it without changing the rest
 */
public final class FileConversationRepository implements ConversationRepository {

    private final Path dir;
    private final Map<String, List<ThreadEntry>> cache = new LinkedHashMap<>();

    public FileConversationRepository(Path dir) {
        this.dir = dir;
        try {
            Files.createDirectories(dir);
            try (Stream<Path> files = Files.list(dir)) {
                for (Path f : files.filter(p -> p.toString().endsWith(".json")).toList()) {
                    load(f);
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public synchronized void append(String threadId, ThreadEntry entry) {
        List<ThreadEntry> entries = cache.computeIfAbsent(threadId, k -> new ArrayList<>());
        boolean duplicate = entries.stream().anyMatch(e -> e.kind() == entry.kind()
                && e.messageId().equals(entry.messageId()) && e.text().equals(entry.text()));
        if (duplicate) {
            return;
        }
        entries.add(entry);
        entries.sort(Comparator.comparing(ThreadEntry::timestamp));
        save(threadId, entries);
    }

    @Override
    public synchronized List<ThreadEntry> thread(String threadId) {
        return List.copyOf(cache.getOrDefault(threadId, List.of()));
    }

    @Override
    public synchronized List<ThreadEntry> recentWith(String address, String excludedThreadId, int limit) {
        List<ThreadEntry> out = new ArrayList<>();
        for (Map.Entry<String, List<ThreadEntry>> t : cache.entrySet()) {
            if (t.getKey().equals(excludedThreadId)) {
                continue;
            }
            boolean withThisPerson = t.getValue().stream().anyMatch(e -> e.author().equalsIgnoreCase(address));
            if (!withThisPerson) {
                continue;
            }
            for (ThreadEntry e : t.getValue()) {
                if (e.kind() == ThreadEntry.Kind.RECEIVED || e.kind() == ThreadEntry.Kind.SENT) {
                    out.add(e);
                }
            }
        }
        out.sort(Comparator.comparing(ThreadEntry::timestamp).reversed());
        return out.subList(0, Math.min(limit, out.size()));
    }

    private void save(String threadId, List<ThreadEntry> entries) {
        List<Object> list = new ArrayList<>();
        for (ThreadEntry e : entries) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("kind", e.kind().name());
            m.put("messageId", e.messageId());
            m.put("author", e.author());
            m.put("timestamp", e.timestamp().toString());
            m.put("subject", e.subject());
            m.put("text", e.text());
            m.put("agentId", e.agentId());
            m.put("note", e.note());
            list.add(m);
        }
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("threadId", threadId);
        root.put("entries", list);
        try {
            Files.writeString(dir.resolve(fileName(threadId)), Json.pretty(root), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @SuppressWarnings("unchecked")
    private void load(Path f) throws IOException {
        Map<String, Object> root = Json.parseObject(Files.readString(f, StandardCharsets.UTF_8));
        String threadId = String.valueOf(root.get("threadId"));
        List<ThreadEntry> entries = new ArrayList<>();
        for (Object o : (List<Object>) root.getOrDefault("entries", List.of())) {
            Map<String, Object> m = (Map<String, Object>) o;
            entries.add(new ThreadEntry(ThreadEntry.Kind.valueOf(str(m, "kind")), str(m, "messageId"),
                    str(m, "author"), LocalDateTime.parse(str(m, "timestamp")), str(m, "subject"), str(m, "text"),
                    str(m, "agentId"), str(m, "note")));
        }
        cache.put(threadId, entries);
    }

    private static String str(Map<String, Object> m, String k) {
        Object v = m.get(k);
        return v == null ? "" : v.toString();
    }

    private static String fileName(String threadId) {
        return threadId.replaceAll("[^a-zA-Z0-9._-]", "_") + ".json";
    }
}
