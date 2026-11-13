package mas.mail.ui;

import mas.mail.app.MailAssistant;
import mas.mail.config.AppConfig;

import javax.swing.BorderFactory;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JTabbedPane;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.UIManager;
import java.awt.BorderLayout;
import java.awt.Dimension;

/** main window: inbox, agent management and organisation trace */
public final class MainWindow extends JFrame {

    private final JLabel status = new JLabel(" ");

    private MainWindow(MailAssistant assistant) {
        super("Multi-agent email assistant (local LLM)");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        TracePanel trace = new TracePanel(assistant);
        AgentsPanel agents = new AgentsPanel(assistant);
        InboxPanel inbox = new InboxPanel(assistant, this::setStatus);
        agents.onAgentsChanged(inbox::refreshAgents);
        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Inbox", inbox);
        tabs.addTab("Agents", agents);
        tabs.addTab("Organisation trace", trace);
        tabs.addChangeListener(e -> {
            if (tabs.getSelectedComponent() == trace) {
                trace.refreshAgents();
            }
        });
        status.setBorder(BorderFactory.createEmptyBorder(3, 8, 3, 8));
        add(tabs, BorderLayout.CENTER);
        add(status, BorderLayout.SOUTH);
        setPreferredSize(new Dimension(1300, 850));
        pack();
        setLocationRelativeTo(null);
        checkLlm(assistant);
    }

    void setStatus(String text) {
        status.setText(text);
    }

    private void checkLlm(MailAssistant assistant) {
        String model = assistant.defaultLlm().modelName();
        setStatus("Checking local LLM '" + model + "'...");
        new SwingWorker<Boolean, Void>() {
            @Override
            protected Boolean doInBackground() {
                return assistant.defaultLlm().isAvailable();
            }

            @Override
            protected void done() {
                try {
                    boolean ok = get();
                    setStatus("LLM: " + assistant.config().get("llm.provider", "?") + " / " + model
                            + (ok ? "  (available)" : "  (NOT available - see README, or start with --mock)")
                            + "   |   routing: " + assistant.routingStrategyName());
                } catch (Exception e) {
                    setStatus("LLM check failed: " + e.getMessage());
                }
            }
        }.execute();
    }

    public static void launch(AppConfig config) {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignored) {
                // keep the default look and feel
            }
            try {
                new MainWindow(new MailAssistant(config)).setVisible(true);
            } catch (RuntimeException e) {
                JOptionPane.showMessageDialog(null, "Startup failed: " + e.getMessage(), "Error",
                        JOptionPane.ERROR_MESSAGE);
                throw e;
            }
        });
    }
}
