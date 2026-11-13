package mas.mail.llm;

/** one message of a chat prompt */
public record ChatMessage(Role role, String content) {

    /** chat roles that every chat API understands */
    public enum Role {
        SYSTEM, USER, ASSISTANT;

        public String apiName() {
            return name().toLowerCase();
        }
    }

    public static ChatMessage system(String content) {
        return new ChatMessage(Role.SYSTEM, content);
    }

    public static ChatMessage user(String content) {
        return new ChatMessage(Role.USER, content);
    }

    public static ChatMessage assistant(String content) {
        return new ChatMessage(Role.ASSISTANT, content);
    }
}
