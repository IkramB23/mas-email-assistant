package mas.mail.routing;

import mas.mail.config.AgentProfile;
import mas.mail.config.ContactDirectory;
import mas.mail.model.Contact;
import mas.mail.model.Email;
import mas.mail.util.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * self check based on rules from the agent's profile
 * it adds up several separate clues, so the choice never depends only on the sender address:
 * - sender address matches one of the agent's patterns (+2.0)
 * - sender role (from the address book) is handled by the agent (+1.5)
 * - sender has a tag of the agent, for example a project member (+1.0)
 * - each keyword found in the subject (+1.5 x w) or else in the body (+1.0 x w)
 * the raw total is turned into [0, 1) with {@code 1 - exp(-raw / 4)}, so each extra clue adds less
 */
public final class ProfileCompetenceEvaluator implements CompetenceEvaluator {

    static final double PATTERN_WEIGHT = 2.0;
    static final double ROLE_WEIGHT = 1.5;
    static final double TAG_WEIGHT = 1.0;
    static final double SUBJECT_WEIGHT = 1.5;
    static final double BODY_WEIGHT = 1.0;
    static final double SCALE = 4.0;

    @Override
    public AgentScore evaluate(AgentProfile p, Email email, Contact sender) {
        double raw = 0;
        List<String> reasons = new ArrayList<>();
        for (String pattern : p.senderPatterns()) {
            if (ContactDirectory.matchesGlob(email.from(), pattern)) {
                raw += PATTERN_WEIGHT;
                reasons.add("sender matches " + pattern);
                break;
            }
        }
        if (!sender.role().isBlank() && p.senderRoles().contains(sender.role())) {
            raw += ROLE_WEIGHT;
            reasons.add("sender role '" + sender.role() + "'");
        }
        for (String tag : sender.tags()) {
            if (p.senderTags().contains(tag)) {
                raw += TAG_WEIGHT;
                reasons.add("sender tagged '" + tag + "'");
                break;
            }
        }
        String subject = Text.normalize(email.subject());
        String body = Text.normalize(email.body());
        List<String> inSubject = new ArrayList<>();
        List<String> inBody = new ArrayList<>();
        for (Map.Entry<String, Double> kw : p.keywords().entrySet()) {
            Pattern pat = Pattern.compile("(?<![\\p{L}\\p{N}])" + Pattern.quote(kw.getKey()));
            if (pat.matcher(subject).find()) {
                raw += SUBJECT_WEIGHT * kw.getValue();
                inSubject.add(kw.getKey());
            } else if (pat.matcher(body).find()) {
                raw += BODY_WEIGHT * kw.getValue();
                inBody.add(kw.getKey());
            }
        }
        if (!inSubject.isEmpty()) {
            reasons.add("subject keywords " + inSubject);
        }
        if (!inBody.isEmpty()) {
            reasons.add("body keywords " + inBody);
        }
        double score = 1 - Math.exp(-raw / SCALE);
        if (!reasons.isEmpty()) {
            reasons.add(0, String.format(Locale.ROOT, "evidence %.1f", raw));
        }
        return new AgentScore(p.id(), score, reasons);
    }
}
