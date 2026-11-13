package mas.mail.context;

/**
 * a piece of knowledge that can be put in a prompt
 *
 * @param source where it comes from (file name...)
 * @param text   the text
 * @param pinned pinned chunks are always sent first (if they fit in the budget)
 */
public record ContextChunk(String source, String text, boolean pinned) {
}
