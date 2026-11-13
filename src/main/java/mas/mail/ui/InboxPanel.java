package mas.mail.ui;

import mas.mail.agent.protocol.DraftResult;
import mas.mail.app.MailAssistant;
import mas.mail.config.AgentProfile;
import mas.mail.model.Email;
import mas.mail.routing.AgentScore;
import mas.mail.routing.RoutingDecision;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultComboBoxModel;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingWorker;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Callable;
import java.util.function.Consumer;

/**
 * inbox: pick an email and see which agent the router chose (and why)
 * read the proposal, then accept it (maybe after editing), reject it with comments,
 * send it to another agent, or throw it away
 */
final class InboxPanel extends JPanel {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final MailAssistant assistant;
    private final Consumer<String> status;
    private final DefaultListModel<Email> emails = new DefaultListModel<>();
    private final JList<Email> emailList = new JList<>(emails);
    private final JTextArea emailView = area(false);
    private final JLabel routingLabel = new JLabel(" ");
    private final JTextArea explanation = area(false);
    private final DefaultTableModel ranking = new DefaultTableModel(new Object[]{"Agent", "Score", "Reasons"}, 0) {
        @Override
        public boolean isCellEditable(int r, int c) {
            return false;
        }
    };
    private final JTextArea draft = area(true);
    private final JLabel draftInfo = new JLabel(" ");
    private final JLabel reviewLabel = new JLabel(" ");
    private final JTextField feedback = new JTextField();
    private final JComboBox<String> agentChoice = new JComboBox<>();
    private final JButton proposeBtn = new JButton("Propose a reply");
    private final JButton acceptBtn = new JButton("Accept & send");
    private final JButton rejectBtn = new JButton("Reject & regenerate");
    private final JButton rerouteBtn = new JButton("Regenerate with agent:");
    private final JButton discardBtn = new JButton("Discard");
    private final JButton promptBtn = new JButton("Show prompt");
    private DraftResult current;

    InboxPanel(MailAssistant assistant, Consumer<String> status) {
        super(new BorderLayout());
        this.assistant = assistant;
        this.status = status;

        emailList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        emailList.setCellRenderer(new EmailRenderer());
        emailList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                showEmail(emailList.getSelectedValue());
            }
        });
        JButton refresh = new JButton("Refresh inbox");
        refresh.addActionListener(e -> refreshInbox());
        JPanel left = new JPanel(new BorderLayout());
        left.add(new JScrollPane(emailList), BorderLayout.CENTER);
        left.add(refresh, BorderLayout.SOUTH);
        left.setPreferredSize(new Dimension(330, 400));

        JTable table = new JTable(ranking);
        table.getColumnModel().getColumn(0).setPreferredWidth(130);
        table.getColumnModel().getColumn(1).setPreferredWidth(50);
        table.getColumnModel().getColumn(2).setPreferredWidth(700);
        JScrollPane tableScroll = new JScrollPane(table);
        tableScroll.setPreferredSize(new Dimension(400, 110));
        routingLabel.setFont(routingLabel.getFont().deriveFont(Font.BOLD, 13f));
        JPanel routing = new JPanel(new BorderLayout(4, 4));
        routing.setBorder(BorderFactory.createTitledBorder("Routing decision"));
        routing.add(routingLabel, BorderLayout.NORTH);
        routing.add(tableScroll, BorderLayout.CENTER);
        JScrollPane explScroll = new JScrollPane(explanation);
        explScroll.setPreferredSize(new Dimension(400, 50));
        routing.add(explScroll, BorderLayout.SOUTH);

        JPanel draftPanel = new JPanel(new BorderLayout(4, 4));
        draftPanel.setBorder(BorderFactory.createTitledBorder("Proposed reply (editable before sending)"));
        JPanel infos = new JPanel();
        infos.setLayout(new BoxLayout(infos, BoxLayout.Y_AXIS));
        infos.add(draftInfo);
        infos.add(reviewLabel);
        draftPanel.add(infos, BorderLayout.NORTH);
        draftPanel.add(new JScrollPane(draft), BorderLayout.CENTER);
        draftPanel.add(controls(), BorderLayout.SOUTH);

        JPanel lower = new JPanel(new BorderLayout());
        lower.add(routing, BorderLayout.NORTH);
        lower.add(draftPanel, BorderLayout.CENTER);

        JPanel emailPanel = new JPanel(new BorderLayout());
        emailPanel.setBorder(BorderFactory.createTitledBorder("Received email"));
        emailPanel.add(new JScrollPane(emailView), BorderLayout.CENTER);
        JPanel proposeBar = new JPanel(new FlowLayout(FlowLayout.LEFT));
        proposeBar.add(proposeBtn);
        emailPanel.add(proposeBar, BorderLayout.SOUTH);

        JSplitPane right = new JSplitPane(JSplitPane.VERTICAL_SPLIT, emailPanel, lower);
        right.setResizeWeight(0.3);
        JSplitPane main = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, left, right);
        add(main, BorderLayout.CENTER);

        proposeBtn.addActionListener(e -> propose());
        acceptBtn.addActionListener(e -> accept());
        rejectBtn.addActionListener(e -> regenerate(null));
        rerouteBtn.addActionListener(e -> regenerate((String) agentChoice.getSelectedItem()));
        discardBtn.addActionListener(e -> discard());
        promptBtn.addActionListener(e -> showPrompt());
        refreshAgents();
        refreshInbox();
        updateButtons(false);
    }

    private JComponent controls() {
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        JPanel fb = new JPanel(new BorderLayout(4, 4));
        fb.add(new JLabel("Feedback for a new proposal: "), BorderLayout.WEST);
        fb.add(feedback, BorderLayout.CENTER);
        p.add(fb);
        p.add(Box.createVerticalStrut(4));
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT));
        buttons.add(acceptBtn);
        buttons.add(rejectBtn);
        buttons.add(rerouteBtn);
        buttons.add(agentChoice);
        buttons.add(discardBtn);
        buttons.add(promptBtn);
        p.add(buttons);
        return p;
    }

    void refreshAgents() {
        DefaultComboBoxModel<String> model = new DefaultComboBoxModel<>();
        for (AgentProfile p : assistant.profiles()) {
            if (p.enabled()) {
                model.addElement(p.id());
            }
        }
        agentChoice.setModel(model);
    }

    private void refreshInbox() {
        Email selected = emailList.getSelectedValue();
        emails.clear();
        assistant.inbox().forEach(emails::addElement);
        if (selected != null) {
            for (int i = 0; i < emails.size(); i++) {
                if (emails.get(i).id().equals(selected.id())) {
                    emailList.setSelectedIndex(i);
                }
            }
        }
        status.accept(emails.size() + " email(s) in the inbox");
    }

    private void showEmail(Email e) {
        current = null;
        clearResult();
        if (e == null) {
            emailView.setText("");
            proposeBtn.setEnabled(false);
            return;
        }
        emailView.setText("From:    " + e.displaySender() + "\nTo:      " + String.join(", ", e.to())
                + (e.cc().isEmpty() ? "" : "\nCc:      " + String.join(", ", e.cc()))
                + "\nDate:    " + e.date().format(DATE) + "\nSubject: " + e.subject()
                + (e.isReply() ? "\nThread:  " + e.threadId() : "") + "\n\n" + e.body());
        emailView.setCaretPosition(0);
        proposeBtn.setEnabled(true);
    }

    private void clearResult() {
        routingLabel.setText(" ");
        explanation.setText("");
        ranking.setRowCount(0);
        draft.setText("");
        draftInfo.setText(" ");
        reviewLabel.setText(" ");
        feedback.setText("");
        updateButtons(false);
    }

    private void propose() {
        Email e = emailList.getSelectedValue();
        if (e != null) {
            runAgents("Routing and drafting a reply for '" + e.subject() + "'...", () -> assistant.process(e));
        }
    }

    private void regenerate(String agentId) {
        if (current == null) {
            return;
        }
        DraftResult prev = current;
        String fb = feedback.getText();
        runAgents("Generating a new proposal...", () -> assistant.regenerate(prev, fb, agentId));
    }

    private void accept() {
        if (current == null) {
            return;
        }
        String id = assistant.accept(current, draft.getText());
        status.accept("Reply validated and written to data/mail/outbox (Message-ID " + id + ")");
        current = null;
        updateButtons(false);
        emailList.repaint();
    }

    private void discard() {
        if (current != null) {
            assistant.discard(current, feedback.getText());
            status.accept("Proposal discarded.");
            current = null;
            clearResult();
        }
    }

    private void showPrompt() {
        if (current == null) {
            return;
        }
        JTextArea a = area(false);
        a.setText(current.proposal().prompt());
        a.setCaretPosition(0);
        JScrollPane sp = new JScrollPane(a);
        sp.setPreferredSize(new Dimension(900, 600));
        JOptionPane.showMessageDialog(this, sp, "Prompt sent to the LLM by " + current.proposal().agentName(),
                JOptionPane.PLAIN_MESSAGE);
    }

    private void runAgents(String message, Callable<DraftResult> task) {
        status.accept(message);
        setBusy(true);
        new SwingWorker<DraftResult, Void>() {
            @Override
            protected DraftResult doInBackground() throws Exception {
                return task.call();
            }

            @Override
            protected void done() {
                setBusy(false);
                try {
                    showResult(get());
                    status.accept("Proposal ready - validate, edit, or reject it.");
                } catch (Exception ex) {
                    Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                    status.accept("Error: " + cause.getMessage());
                    JOptionPane.showMessageDialog(InboxPanel.this, cause.getMessage(), "Error",
                            JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }

    private void showResult(DraftResult r) {
        current = r;
        RoutingDecision d = r.routing();
        routingLabel.setText(String.format(Locale.ROOT, "%s  ->  %s   (confidence %.2f)", d.status(),
                r.proposal().agentName(), d.confidence()));
        routingLabel.setForeground(switch (d.status()) {
            case CONFIDENT -> new Color(0, 120, 0);
            case AMBIGUOUS -> new Color(200, 110, 0);
            case FALLBACK -> new Color(170, 0, 0);
            case MANUAL -> new Color(0, 70, 160);
        });
        ranking.setRowCount(0);
        for (AgentScore s : d.ranking()) {
            ranking.addRow(new Object[]{s.agentId(), String.format(Locale.ROOT, "%.2f", s.score()),
                    String.join(" | ", s.reasons())});
        }
        explanation.setText(d.explanation());
        explanation.setCaretPosition(0);
        draft.setText(r.proposal().text());
        draft.setCaretPosition(0);
        draftInfo.setText(String.format(Locale.ROOT,
                "Proposal #%d by %s  |  model %s  |  %d ms  |  ~%d prompt tokens  |  history: %d msg  |  context: %s",
                r.attempt(), r.proposal().agentName(), r.proposal().model(), r.proposal().durationMs(),
                r.proposal().promptTokens(), r.proposal().historyEntries(),
                r.proposal().contextSources().isEmpty() ? "none" : String.join(", ", r.proposal().contextSources())));
        if (r.review().ok() && r.review().warnings().isEmpty()) {
            reviewLabel.setText("Review: OK");
            reviewLabel.setForeground(new Color(0, 120, 0));
        } else {
            reviewLabel.setText("Review: " + String.join(" ", r.review().issues()) + " "
                    + String.join(" ", r.review().warnings()));
            reviewLabel.setForeground(r.review().ok() ? new Color(200, 110, 0) : new Color(170, 0, 0));
        }
        if (d.status() == RoutingDecision.Status.AMBIGUOUS && d.ranking().size() > 1) {
            agentChoice.setSelectedItem(d.ranking().get(1).agentId());
        }
        feedback.setText("");
        updateButtons(true);
    }

    private void setBusy(boolean busy) {
        proposeBtn.setEnabled(!busy);
        emailList.setEnabled(!busy);
        if (busy) {
            updateButtons(false);
        }
        setCursor(busy ? java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.WAIT_CURSOR) : null);
    }

    private void updateButtons(boolean hasProposal) {
        for (JComponent c : List.<JComponent>of(acceptBtn, rejectBtn, rerouteBtn, agentChoice, discardBtn, promptBtn,
                feedback)) {
            c.setEnabled(hasProposal);
        }
    }

    private static JTextArea area(boolean editable) {
        JTextArea a = new JTextArea();
        a.setEditable(editable);
        a.setLineWrap(true);
        a.setWrapStyleWord(true);
        a.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        return a;
    }

    private final class EmailRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean selected,
                                                      boolean focus) {
            Email e = (Email) value;
            String mark = assistant.isReplied(e) ? "&#10004; " : "";
            String html = "<html><b>" + mark + escape(e.subject()) + "</b><br><small>" + escape(e.displaySender())
                    + " - " + e.date().format(DATE) + "</small></html>";
            JLabel l = (JLabel) super.getListCellRendererComponent(list, html, index, selected, focus);
            l.setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));
            return l;
        }
    }

    private static String escape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
