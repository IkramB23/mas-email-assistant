package mas.mail.eval;

import mas.mail.agent.protocol.DraftOptions;
import mas.mail.agent.protocol.DraftResult;
import mas.mail.app.MailAssistant;
import mas.mail.config.AppConfig;
import mas.mail.mail.DirectoryMailSource;
import mas.mail.model.Email;
import mas.mail.routing.RoutingDecision;
import mas.mail.util.Text;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * runs experiments on a labelled test set ({@code data/testset/*.eml})
 * each test email has these headers:
 * - {@code X-Expected-Agent}: the right agent(s), split by '|' (more than one = ambiguous email)
 * - {@code X-Scenario}: short description
 * - {@code X-Expected-Facts}: facts from the agents' context that a good reply should have, split by ';'
 * <p>
 * routing test: accuracy, ambiguity detection and confusion matrix for several routing setups
 * draft test (--drafts): turns off context, system prompt or specialisation (one generic agent)
 * it measures fact coverage, review problems, length and time
 */
public final class Evaluator {

    private final AppConfig config;
    private final Path outDir;
    private final String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));

    public Evaluator(AppConfig config) {
        this.config = config;
        this.outDir = config.path("eval.output", "results");
        // experiments must not change the real memory or the real trace
        config.set("paths.memory", outDir.resolve("memory-" + stamp).toString());
        config.set("paths.log", outDir.resolve("trace-" + stamp + ".log").toString());
    }

    /** one labelled test case */
    record Case(Email email, List<String> expected, String scenario, List<String> facts) {
        boolean ambiguous() {
            return expected.size() > 1;
        }
    }

    public void run(boolean withDrafts) throws IOException {
        MailAssistant assistant = new MailAssistant(config);
        List<Case> cases = loadCases(config.path("eval.testset", "data/testset"));
        if (cases.isEmpty()) {
            System.out.println("No test case found.");
            return;
        }
        StringBuilder md = new StringBuilder();
        md.append("# Experiment report ").append(stamp).append("\n\n");
        md.append("- Model: `").append(assistant.defaultLlm().modelName()).append("` (available: ")
                .append(assistant.defaultLlm().isAvailable()).append(")\n");
        md.append("- Test cases: ").append(cases.size()).append("\n\n");

        md.append("## 1. Routing\n\n");
        md.append("| Configuration | Accuracy | Ambiguous detected | Fallback correct | Avg time (ms) |\n");
        md.append("|---|---|---|---|---|\n");
        StringBuilder details = new StringBuilder();
        for (String spec : config.get("eval.routingConfigs", "rules;llm;rules:1,llm:1;rules:1,llm:1,thread:0.5")
                .split(";")) {
            assistant.setRoutingStrategy(spec.trim());
            routingExperiment(assistant, cases, spec.trim(), md, details);
        }
        md.append("\n").append(details);

        if (withDrafts) {
            assistant.setRoutingStrategy(config.get("routing.strategies", "rules:1.0,llm:1.0,thread:0.5"));
            draftExperiment(assistant, cases, md);
        }
        Files.createDirectories(outDir);
        Path report = outDir.resolve("eval-" + stamp + ".md");
        Files.writeString(report, md.toString(), StandardCharsets.UTF_8);
        System.out.println(md);
        System.out.println("Report written to " + report);
    }

    private void routingExperiment(MailAssistant a, List<Case> cases, String spec, StringBuilder md,
                                   StringBuilder details) {
        int correct = 0;
        int ambiguousCases = 0;
        int ambiguousDetected = 0;
        int fallbackCases = 0;
        int fallbackCorrect = 0;
        long time = 0;
        String fallback = config.get("routing.fallbackAgent", "generic");
        Map<String, Map<String, Integer>> confusion = new TreeMap<>();
        StringBuilder rows = new StringBuilder();
        for (Case c : cases) {
            long t0 = System.currentTimeMillis();
            RoutingDecision d = a.previewRouting(c.email());
            time += System.currentTimeMillis() - t0;
            boolean ok = c.expected().contains(d.selectedAgentId());
            if (ok) {
                correct++;
            }
            if (c.ambiguous()) {
                ambiguousCases++;
                if (d.status() == RoutingDecision.Status.AMBIGUOUS) {
                    ambiguousDetected++;
                }
            }
            if (c.expected().contains(fallback) && c.expected().size() == 1) {
                fallbackCases++;
                if (d.status() == RoutingDecision.Status.FALLBACK) {
                    fallbackCorrect++;
                }
            }
            // when more than one agent is right (ambiguous case), a right choice counts on the diagonal
            String row = ok ? d.selectedAgentId() : c.expected().get(0);
            confusion.computeIfAbsent(row, k -> new TreeMap<>())
                    .merge(d.selectedAgentId(), 1, Integer::sum);
            rows.append(String.format(Locale.ROOT, "| %s | %s | %s | %s %.2f | %s |%n", ok ? "OK" : "**KO**",
                    Text.truncate(c.scenario(), 60), String.join(" or ", c.expected()), d.selectedAgentId(),
                    d.confidence(), d.status()));
        }
        md.append(String.format(Locale.ROOT, "| `%s` | %d/%d (%.0f%%) | %d/%d | %d/%d | %d |%n", spec, correct,
                cases.size(), 100.0 * correct / cases.size(), ambiguousDetected, ambiguousCases, fallbackCorrect,
                fallbackCases, time / cases.size()));
        details.append("### Details for `").append(spec).append("`\n\n");
        details.append("| | Scenario | Expected | Selected | Status |\n|---|---|---|---|---|\n").append(rows);
        details.append("\nConfusion matrix (rows = expected, columns = selected)\n\n");
        TreeSet<String> cols = new TreeSet<>();
        confusion.values().forEach(m -> cols.addAll(m.keySet()));
        cols.addAll(confusion.keySet());
        details.append("| expected \\ selected |");
        cols.forEach(col -> details.append(' ').append(col).append(" |"));
        details.append("\n|---|").append("---|".repeat(cols.size())).append('\n');
        for (String row : cols) {
            if (!confusion.containsKey(row)) {
                continue;
            }
            details.append("| ").append(row).append(" |");
            for (String col : cols) {
                details.append(' ').append(confusion.get(row).getOrDefault(col, 0)).append(" |");
            }
            details.append('\n');
        }
        details.append('\n');
    }

    private void draftExperiment(MailAssistant a, List<Case> cases, StringBuilder md) {
        String generic = config.get("routing.fallbackAgent", "generic");
        Map<String, DraftConfig> configs = new LinkedHashMap<>();
        configs.put("multi-agent (full)", new DraftConfig(null, DraftOptions.FULL));
        configs.put("no context", new DraftConfig(null, new DraftOptions(false, true, true)));
        configs.put("no system prompt", new DraftConfig(null, new DraftOptions(true, false, true)));
        configs.put("single generic agent", new DraftConfig(generic, DraftOptions.FULL));

        md.append("## 2. Drafts (ablation)\n\n");
        md.append("Fact coverage = share of the expected facts (from the agents' private context) present in the draft.\n\n");
        md.append("| Configuration | Fact coverage | Drafts with review issues | Avg words | Avg time (ms) |\n");
        md.append("|---|---|---|---|---|\n");
        StringBuilder samples = new StringBuilder("\n### Generated drafts\n\n");
        int limit = config.getInt("eval.maxDraftCases", 6);
        List<Case> subset = cases.stream().filter(c -> !c.facts().isEmpty()).limit(limit).toList();
        for (Map.Entry<String, DraftConfig> cfg : configs.entrySet()) {
            int facts = 0;
            int found = 0;
            int withIssues = 0;
            long words = 0;
            long time = 0;
            for (Case c : subset) {
                DraftResult r;
                try {
                    r = a.process(c.email(), cfg.getValue().forcedAgent(), cfg.getValue().options());
                } catch (RuntimeException e) {
                    samples.append("- ").append(cfg.getKey()).append(" / ").append(c.scenario()).append(": ERROR ")
                            .append(e.getMessage()).append('\n');
                    continue;
                }
                String norm = Text.normalize(r.proposal().text());
                for (String f : c.facts()) {
                    facts++;
                    if (norm.contains(Text.normalize(f))) {
                        found++;
                    }
                }
                if (!r.review().ok()) {
                    withIssues++;
                }
                words += r.proposal().text().split("\\s+").length;
                time += r.proposal().durationMs();
                samples.append("#### ").append(cfg.getKey()).append(" - ").append(c.scenario()).append(" (agent ")
                        .append(r.proposal().agentId()).append(")\n\n```\n").append(r.proposal().text())
                        .append("\n```\n\n");
            }
            int n = Math.max(1, subset.size());
            md.append(String.format(Locale.ROOT, "| %s | %d/%d (%.0f%%) | %d/%d | %d | %d |%n", cfg.getKey(), found,
                    facts, facts == 0 ? 0.0 : 100.0 * found / facts, withIssues, subset.size(), words / n, time / n));
            System.out.println("Draft configuration done: " + cfg.getKey());
        }
        md.append(samples);
    }

    record DraftConfig(String forcedAgent, DraftOptions options) {
    }

    private static List<Case> loadCases(Path dir) {
        List<Case> out = new ArrayList<>();
        for (Email e : new DirectoryMailSource(dir).fetch()) {
            List<String> expected = new ArrayList<>();
            for (String s : e.header("x-expected-agent").split("\\|")) {
                if (!s.isBlank()) {
                    expected.add(s.trim());
                }
            }
            if (expected.isEmpty()) {
                continue;
            }
            List<String> facts = new ArrayList<>();
            for (String f : e.header("x-expected-facts").split(";")) {
                if (!f.isBlank()) {
                    facts.add(f.trim());
                }
            }
            String scenario = e.header("x-scenario").isBlank() ? e.subject() : e.header("x-scenario");
            out.add(new Case(e, expected, scenario, facts));
        }
        return out;
    }
}
