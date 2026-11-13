package mas.mail.agent;

import mas.mail.agent.protocol.DraftRequest;
import mas.mail.agent.protocol.HistoryAnswer;
import mas.mail.config.AgentProfile;
import mas.mail.config.UserProfile;
import mas.mail.context.ContextChunk;
import mas.mail.llm.ChatMessage;
import mas.mail.model.Email;
import mas.mail.model.ThreadEntry;
import mas.mail.util.Text;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * builds the prompt of a specialist from clear separate sections
 * small local models follow instructions better when role, rules, knowledge and task are clearly split
 */
public final class PromptBuilder {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final String NEUTRAL_PROMPT = "Reply politely and helpfully to the email.";

    private final UserProfile user;

    public PromptBuilder(UserProfile user) {
        this.user = user;
    }

    public List<ChatMessage> build(AgentProfile profile, DraftRequest r, List<ContextChunk> context,
                                   HistoryAnswer history) {
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(ChatMessage.system(systemPart(profile, r)));
        // the language rule is repeated at the end
        // small models follow the last instruction best
        messages.add(ChatMessage.user(userPart(r, context, history) + " " + languageRule(profile, r.email())));
        return messages;
    }

    private String systemPart(AgentProfile p, DraftRequest r) {
        StringBuilder sb = new StringBuilder();
        sb.append("You draft email replies on behalf of ").append(user.name());
        if (!user.title().isBlank()) {
            sb.append(" (").append(user.title()).append(')');
        }
        sb.append(". Write as ").append(user.name()).append(", in the first person.\n\n");
        sb.append("# Role and instructions\n");
        sb.append(r.options().useSystemPrompt() && !p.systemPrompt().isBlank() ? p.systemPrompt().strip()
                : NEUTRAL_PROMPT).append("\n\n");
        if (r.options().useSystemPrompt() && !user.preferences().isBlank()) {
            sb.append("# General preferences of ").append(user.name()).append('\n')
                    .append(user.preferences().strip()).append("\n\n");
        }
        sb.append("# Output rules\n");
        sb.append("- Output ONLY the body of the reply email: no subject line, no comments, no markdown.\n");
        sb.append("- ").append(languageRule(p, r.email())).append('\n');
        sb.append("- Only use facts found in the email, the conversation history or the reference documents. ")
                .append("If an information is missing, say you will check and come back; never invent dates, ")
                .append("numbers, names or commitments.\n");
        sb.append("- Never claim that something has already been done, sent or attached unless the history ")
                .append("says so.\n");
        // the forbidden values are never written in the prompt
        // they are removed from the context before
        // the model is only told what the removal markers mean
        sb.append("- Information shown as [CONFIDENTIAL] must never be mentioned, guessed or paraphrased.\n");
        sb.append("- At most ").append(p.maxWords()).append(" words.\n");
        sb.append("- End with a closing formula (e.g. \"Best regards,\" / \"Bien cordialement,\") but do NOT write ")
                .append("any name or signature: it is added automatically.\n");
        return sb.toString();
    }

    public String signature() {
        return user.signature();
    }

    /** information this agent must never share (its own rules and the user's rules) */
    public Set<String> forbiddenTerms(AgentProfile p) {
        Set<String> forbidden = new LinkedHashSet<>(p.forbiddenTerms());
        forbidden.addAll(user.forbiddenTerms());
        return forbidden;
    }

    private static String languageRule(AgentProfile p, Email email) {
        String lang = p.language();
        if ("auto".equalsIgnoreCase(lang)) {
            lang = Text.guessLanguage(email.subject() + " " + email.body());
        }
        return switch (lang.toLowerCase()) {
            case "fr" -> "Write the reply in French.";
            case "en" -> "Write the reply in English.";
            case "unknown", "auto" -> "Write the reply in the same language as the email to answer.";
            default -> "Write the reply in " + lang + ".";
        };
    }

    private String userPart(DraftRequest r, List<ContextChunk> context, HistoryAnswer history) {
        StringBuilder sb = new StringBuilder();
        sb.append("## Sender\n").append(r.sender().describe()).append("\n\n");
        if (!context.isEmpty()) {
            sb.append("## Reference documents\n");
            for (ContextChunk c : context) {
                sb.append("[").append(c.source()).append("]\n").append(c.text().strip()).append("\n\n");
            }
        }
        if (!history.withSender().isEmpty()) {
            sb.append("## Earlier exchanges with this sender (other conversations)\n");
            appendEntries(sb, history.withSender());
        }
        if (!history.thread().isEmpty()) {
            sb.append("## Conversation history (oldest first)\n");
            appendEntries(sb, history.thread());
        }
        Email e = r.email();
        sb.append("## Email to answer\n");
        sb.append("From: ").append(e.displaySender()).append('\n');
        sb.append("Date: ").append(e.date().format(DATE)).append('\n');
        sb.append("Subject: ").append(e.subject()).append("\n\n");
        sb.append(e.body().strip()).append("\n\n");
        if (r.isRevision()) {
            if (r.previousDraft() != null && !r.previousDraft().isBlank()) {
                sb.append("## Previous draft (rejected)\n").append(r.previousDraft().strip()).append("\n\n");
            }
            sb.append("## Feedback to take into account\n").append(r.feedback().strip()).append("\n\n");
            sb.append("Write an improved reply now.");
        } else {
            sb.append("Write the reply now.");
        }
        return sb.toString();
    }

    private void appendEntries(StringBuilder sb, List<ThreadEntry> entries) {
        for (ThreadEntry t : entries) {
            String who = t.kind() == ThreadEntry.Kind.SENT ? "reply sent by " + user.name() : "message from " + t.author();
            sb.append("--- ").append(who).append(", ").append(t.timestamp().format(DATE))
                    .append(" | ").append(t.subject()).append('\n')
                    .append(t.text().strip()).append("\n\n");
        }
    }
}
