package mas.mail.context;

import java.util.List;

/**
 * a source of knowledge that belongs to one agent (folder of documents, notes, database...)
 * to add a new kind of source, implement this interface
 */
public interface ContextSource {

    String name();

    /** all the chunks available now in this source */
    List<ContextChunk> chunks();
}
