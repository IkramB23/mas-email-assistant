package mas.mail.app;

import mas.mail.agent.protocol.DraftResult;
import mas.mail.model.Email;
import mas.mail.routing.AgentScore;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;

/**
 * simple text interface
 * same steps as the GUI: propose, then accept, reject with comments, or skip
 */
public final class ConsoleApp {

    private final MailAssistant assistant;
    private final BufferedReader in = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));

    public ConsoleApp(MailAssistant assistant) {
        this.assistant = assistant;
    }

    public void run() throws IOException {
        System.out.println("LLM: " + assistant.defaultLlm().modelName() + " (available: "
                + assistant.defaultLlm().isAvailable() + ") | routing: " + assistant.routingStrategyName());
        List<Email> emails = assistant.inbox();
        for (Email e : emails) {
            if (assistant.isReplied(e)) {
                continue;
            }
            System.out.println("\n================================================================");
            System.out.println("From:    " + e.displaySender());
            System.out.println("Subject: " + e.subject());
            System.out.println("----------------------------------------------------------------");
            System.out.println(e.body());
            DraftResult r;
            try {
                r = assistant.process(e);
            } catch (RuntimeException ex) {
                System.out.println("!! Processing failed: " + ex.getMessage());
                continue;
            }
            while (true) {
                print(r);
                System.out.print("\n[a]ccept  [r]eject + feedback  [o]ther agent  [s]kip  [q]uit > ");
                String choice = in.readLine();
                if (choice == null || choice.startsWith("q")) {
                    return;
                }
                if (choice.startsWith("a")) {
                    System.out.println("Sent to outbox: " + assistant.accept(r, r.proposal().text()));
                    break;
                } else if (choice.startsWith("r") || choice.startsWith("o")) {
                    String id = null;
                    if (choice.startsWith("o")) {
                        System.out.print("Agent id > ");
                        String line = in.readLine();
                        id = line == null ? null : line.trim();
                    }
                    System.out.print("Feedback" + (id == null ? "" : " (optional)") + " > ");
                    try {
                        r = assistant.regenerate(r, in.readLine(), id);
                    } catch (RuntimeException ex) {
                        System.out.println("!! Regeneration failed: " + ex.getMessage());
                    }
                } else {
                    assistant.discard(r, "skipped");
                    break;
                }
            }
        }
        System.out.println("\nNo more emails to process.");
    }

    private static void print(DraftResult r) {
        System.out.println("\n--- Routing: " + r.routing().status() + " -> " + r.proposal().agentName()
                + String.format(Locale.ROOT, " (%.2f)", r.routing().confidence()));
        for (AgentScore s : r.routing().ranking()) {
            System.out.printf(Locale.ROOT, "    %-18s %.2f%n", s.agentId(), s.score());
        }
        System.out.println("    " + r.routing().explanation());
        System.out.println("--- Proposal #" + r.attempt() + " (" + r.proposal().model() + ", "
                + r.proposal().durationMs() + " ms, context: " + r.proposal().contextSources() + ")");
        System.out.println(r.proposal().text());
        if (!r.review().issues().isEmpty() || !r.review().warnings().isEmpty()) {
            System.out.println("--- Review: issues " + r.review().issues() + " warnings " + r.review().warnings());
        }
    }
}
