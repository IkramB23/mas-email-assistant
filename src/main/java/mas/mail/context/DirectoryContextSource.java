package mas.mail.context;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/**
 * reads the text files (.txt, .md, .csv, .eml) of a folder and cuts them into chunks by paragraph
 * files in a {@code pinned/} sub-folder are marked as pinned
 * chunks are kept in cache and read again only when a file changes
 */
public final class DirectoryContextSource implements ContextSource {

    private static final Set<String> EXTENSIONS = Set.of("txt", "md", "csv", "eml", "json");
    private static final int CHUNK_CHARS = 900;

    private final Path dir;
    private final Map<Path, FileTime> stamps = new HashMap<>();
    private final Map<Path, List<ContextChunk>> cache = new HashMap<>();

    public DirectoryContextSource(Path dir) {
        this.dir = dir;
    }

    @Override
    public String name() {
        return dir.toString();
    }

    @Override
    public synchronized List<ContextChunk> chunks() {
        List<ContextChunk> out = new ArrayList<>();
        if (!Files.isDirectory(dir)) {
            return out;
        }
        try (Stream<Path> walk = Files.walk(dir)) {
            List<Path> files = walk.filter(Files::isRegularFile).filter(this::supported).sorted().toList();
            cache.keySet().retainAll(files);
            for (Path f : files) {
                FileTime t = Files.getLastModifiedTime(f);
                if (!t.equals(stamps.get(f))) {
                    cache.put(f, split(f));
                    stamps.put(f, t);
                }
                out.addAll(cache.get(f));
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return out;
    }

    private boolean supported(Path f) {
        String n = f.getFileName().toString();
        int dot = n.lastIndexOf('.');
        return dot > 0 && EXTENSIONS.contains(n.substring(dot + 1).toLowerCase());
    }

    private List<ContextChunk> split(Path f) throws IOException {
        String text = Files.readString(f, StandardCharsets.UTF_8).replace("\r\n", "\n");
        boolean pinned = dir.relativize(f).toString().replace('\\', '/').startsWith("pinned/");
        String source = dir.relativize(f).toString().replace('\\', '/');
        List<ContextChunk> chunks = new ArrayList<>();
        if (pinned) {
            // pinned documents are short on purpose and must stay in one piece
            chunks.add(new ContextChunk(source, text.strip(), true));
            return chunks;
        }
        StringBuilder current = new StringBuilder();
        for (String para : text.split("\n\\s*\n")) {
            if (current.length() + para.length() > CHUNK_CHARS && current.length() > 0) {
                chunks.add(new ContextChunk(source, current.toString().strip(), false));
                current.setLength(0);
            }
            current.append(para).append("\n\n");
        }
        if (!current.toString().isBlank()) {
            chunks.add(new ContextChunk(source, current.toString().strip(), false));
        }
        return chunks;
    }
}
