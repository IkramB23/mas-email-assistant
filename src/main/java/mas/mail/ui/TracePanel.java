package mas.mail.ui;

import mas.mail.agent.Agent;
import mas.mail.app.MailAssistant;
import mas.mail.app.MessageFormatter;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Font;

/** live view of the organisation: the agents and every message sent on the bus */
final class TracePanel extends JPanel {

    private final MailAssistant assistant;
    private final JLabel agents = new JLabel();
    private final JTextArea log = new JTextArea();

    TracePanel(MailAssistant assistant) {
        super(new BorderLayout());
        this.assistant = assistant;
        log.setEditable(false);
        log.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        assistant.addListener(m -> {
            String line = MessageFormatter.line(m) + "\n";
            SwingUtilities.invokeLater(() -> log.append(line));
        });
        JButton clear = new JButton("Clear");
        clear.addActionListener(e -> log.setText(""));
        JPanel top = new JPanel(new BorderLayout());
        top.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));
        top.add(agents, BorderLayout.CENTER);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttons.add(clear);
        top.add(buttons, BorderLayout.EAST);
        add(top, BorderLayout.NORTH);
        add(new JScrollPane(log), BorderLayout.CENTER);
        refreshAgents();
    }

    void refreshAgents() {
        StringBuilder sb = new StringBuilder("<html><b>Organisation:</b> ");
        for (Agent a : assistant.agents()) {
            sb.append(a.id()).append(" <small>(").append(a.role().name().toLowerCase()).append(")</small> &nbsp; ");
        }
        agents.setText(sb.append("</html>").toString());
    }
}
