package mas.mail.agent.protocol;

import java.util.List;

/**
 * result of checking a draft
 *
 * @param issues   blocking problems (the draft is fixed automatically)
 * @param warnings small remarks shown to the user
 */
public record ReviewReport(List<String> issues, List<String> warnings) {

    public static final ReviewReport NOT_REVIEWED = new ReviewReport(List.of(), List.of("not reviewed"));

    public ReviewReport {
        issues = List.copyOf(issues);
        warnings = List.copyOf(warnings);
    }

    public boolean ok() {
        return issues.isEmpty();
    }
}
