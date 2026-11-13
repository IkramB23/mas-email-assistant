package mas.mail.app;

import mas.mail.agent.protocol.CompetenceQuery;
import mas.mail.agent.protocol.DraftProposal;
import mas.mail.agent.protocol.DraftRequest;
import mas.mail.agent.protocol.DraftResult;
import mas.mail.agent.protocol.HistoryAnswer;
import mas.mail.agent.protocol.HistoryQuery;
import mas.mail.agent.protocol.MemoryRecord;
import mas.mail.agent.protocol.ProcessEmail;
import mas.mail.agent.protocol.Regenerate;
import mas.mail.agent.protocol.ReviewReport;
import mas.mail.agent.protocol.ReviewRequest;
import mas.mail.comm.AclMessage;
import mas.mail.routing.AgentScore;
import mas.mail.util.Text;

import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** short one-line text for each agent message (for the trace panel and the log file) */
public final class MessageFormatter {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");

    private MessageFormatter() {
    }

    public static String line(AclMessage m) {
        return String.format(Locale.ROOT, "%s [%s] %-10s -> %-16s %-14s %s", m.timestamp().format(TIME),
                m.conversationId(), m.sender(), m.receiver(), m.performative(), summary(m.content()));
    }

    public static String summary(Object c) {
        if (c == null) {
            return "";
        }
        return switch (c) {
            case ProcessEmail p -> "process " + p.email() + (p.forcedAgentId() != null ? " forced=" + p.forcedAgentId() : "");
            case Regenerate g -> "regenerate (feedback: " + Text.truncate(String.valueOf(g.feedback()), 80) + ")"
                    + (g.agentId() != null ? " with " + g.agentId() : "");
            case CompetenceQuery q -> "competence? '" + q.email().subject() + "'";
            case AgentScore s -> String.format(Locale.ROOT, "bid %.2f %s", s.score(), s.reasons());
            case DraftRequest r -> (r.isRevision() ? "revise draft: " + Text.truncate(r.feedback(), 80) : "draft reply")
                    + " for '" + r.email().subject() + "'";
            case DraftProposal p -> String.format(Locale.ROOT, "draft by %s (%d chars, ~%d prompt tokens, %d ms, context %s)",
                    p.agentId(), p.text().length(), p.promptTokens(), p.durationMs(), p.contextSources());
            case ReviewRequest r -> "review draft of " + r.draft().agentId();
            case ReviewReport r -> r.ok() ? "OK " + r.warnings() : "ISSUES " + r.issues();
            case HistoryQuery q -> "history? thread=" + q.threadId() + (q.rawThread() ? " (raw)" : " budget=" + q.tokenBudget());
            case HistoryAnswer a -> a.thread().size() + " thread entries, " + a.withSender().size() + " with sender";
            case MemoryRecord r -> "remember " + r.entry().kind() + " in " + r.threadId();
            case DraftResult r -> String.format(Locale.ROOT, "result: %s -> %s (%s, %.2f)", r.email().subject(),
                    r.proposal().agentId(), r.routing().status(), r.routing().confidence());
            default -> Text.truncate(c.toString(), 120);
        };
    }
}
