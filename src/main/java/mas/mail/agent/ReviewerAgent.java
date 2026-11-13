package mas.mail.agent;

import mas.mail.agent.protocol.ReviewReport;
import mas.mail.agent.protocol.ReviewRequest;
import mas.mail.comm.AclMessage;
import mas.mail.comm.Performative;
import mas.mail.util.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * checks every draft before the user sees it
 * it uses fixed rules and no LLM
 * so it is fast and does not have the same weak points as the model it checks
 * a blocking problem makes the specialist fix the draft once, automatically
 * warnings are only shown to the user
 */
public final class ReviewerAgent extends AbstractAgent {

    private static final Pattern PLACEHOLDER = Pattern.compile(
            "\\[(your|votre|insert|nom|name|date|recipient|destinataire)[^\\]]*\\]|<[A-Z_ ]{3,}>|XX/XX|\\?\\?\\?",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern AI_MENTION = Pattern.compile(
            "as an ai|en tant qu'(ia|intelligence artificielle)|language model|mod[eè]le de langage",
            Pattern.CASE_INSENSITIVE);
    /**
     * the model "thinks out loud" instead of writing the email
     * this happens often with small models or reasoning models
     */
    private static final Pattern META_TEXT = Pattern.compile(
            "^(okay|ok,|alright|let me|i need to|first,)|draft (an|the) (email|reply)|the user'?s (query|request)"
                    + "|word count|reference documents",
            Pattern.CASE_INSENSITIVE);

    private final String signature;

    public ReviewerAgent(String id, String signature) {
        super(id, "Reviewer");
        this.signature = signature == null ? "" : signature.strip();
    }

    @Override
    public AgentRole role() {
        return AgentRole.REVIEWER;
    }

    @Override
    protected AclMessage onRequest(AclMessage m) {
        return m.reply(Performative.INFORM, review(m.contentAs(ReviewRequest.class)));
    }

    ReviewReport review(ReviewRequest r) {
        List<String> issues = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        String full = r.draft().text().strip();
        // the signature is added by the code, not by the LLM
        // so we only check what the LLM really wrote
        String text = !signature.isEmpty() && full.endsWith(signature)
                ? full.substring(0, full.length() - signature.length()).strip() : full;
        if (text.split("\\s+").length < 8) {
            issues.add("The draft has no real content: write a complete reply to the email.");
            return new ReviewReport(issues, warnings);
        }
        if (PLACEHOLDER.matcher(text).find()) {
            issues.add("The draft contains a placeholder (e.g. [Your Name]) instead of real content.");
        }
        if (AI_MENTION.matcher(text).find()) {
            issues.add("The draft mentions being an AI / a language model.");
        }
        if (META_TEXT.matcher(text).find()) {
            issues.add("The draft contains reasoning or comments about the task instead of only the email body.");
        }
        String normDraft = Text.normalize(text);
        String normEmail = Text.normalize(r.email().body());
        for (String term : r.forbiddenTerms()) {
            String t = Text.normalize(term);
            if (!t.isBlank() && normDraft.contains(t) && !normEmail.contains(t)) {
                issues.add("The draft discloses a forbidden information: \"" + term + "\".");
            }
        }
        int words = text.split("\\s+").length;
        if (words > r.maxWords() * 1.3) {
            warnings.add("The draft is long (" + words + " words, limit " + r.maxWords() + ").");
        }
        String expected = r.expectedLanguage();
        if ("auto".equalsIgnoreCase(expected)) {
            expected = Text.guessLanguage(r.email().subject() + " " + r.email().body());
        }
        String actual = Text.guessLanguage(text);
        if (("fr".equals(expected) || "en".equals(expected)) && !"unknown".equals(actual) && !actual.equals(expected)) {
            issues.add("The draft is written in the wrong language ('" + actual + "'): it must be entirely written in '"
                    + expected + "'.");
        }
        return new ReviewReport(issues, warnings);
    }
}
