package mas.mail.ui;

import mas.mail.app.MailAssistant;
import mas.mail.config.AgentProfile;
import mas.mail.config.FileAgentProfileRepository;
import mas.mail.util.Text;

import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * agent management: list, create, change (system prompt, routing hints, budgets), delete,
 * and add private context documents
 * changes are saved in {@code data/agents/} and the organisation is rebuilt right away
 * no restart and no code change needed
 */
final class AgentsPanel extends JPanel {

    private final MailAssistant assistant;
    private final DefaultListModel<AgentProfile> model = new DefaultListModel<>();
    private final JList<AgentProfile> list = new JList<>(model);
    private final JTextField id = new JTextField();
    private final JTextField name = new JTextField();
    private final JTextField description = new JTextField();
    private final JCheckBox enabled = new JCheckBox("enabled");
    private final JCheckBox fallback = new JCheckBox("fallback agent (used when no specialist fits)");
    private final JTextField keywords = new JTextField();
    private final JTextField patterns = new JTextField();
    private final JTextField roles = new JTextField();
    private final JTextField tags = new JTextField();
    private final JComboBox<String> language = new JComboBox<>(new String[]{"auto", "fr", "en"});
    private final JSpinner contextTokens = new JSpinner(new SpinnerNumberModel(900, 0, 20000, 100));
    private final JSpinner historyTokens = new JSpinner(new SpinnerNumberModel(700, 0, 20000, 100));
    private final JSpinner maxWords = new JSpinner(new SpinnerNumberModel(250, 20, 2000, 10));
    private final JTextField forbidden = new JTextField();
    private final JTextField modelOverride = new JTextField();
    private final JTextArea prompt = new JTextArea();
    private final DefaultListModel<String> files = new DefaultListModel<>();
    private final JList<String> fileList = new JList<>(files);
    private Runnable onChange = () -> {
    };
    private boolean creating;

    AgentsPanel(MailAssistant assistant) {
        super(new BorderLayout());
        this.assistant = assistant;
        list.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> l, Object v, int i, boolean s, boolean f) {
                AgentProfile p = (AgentProfile) v;
                String label = "<html><b>" + p.displayName() + "</b> <small>(" + p.id() + ")"
                        + (p.fallback() ? " fallback" : "") + (p.enabled() ? "" : " disabled") + "</small><br><small>"
                        + Text.truncate(p.description(), 60) + "</small></html>";
                JLabel c = (JLabel) super.getListCellRendererComponent(l, label, i, s, f);
                c.setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));
                return c;
            }
        });
        list.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting() && list.getSelectedValue() != null) {
                show(list.getSelectedValue());
            }
        });
        JButton newBtn = new JButton("New agent");
        JButton deleteBtn = new JButton("Delete");
        newBtn.addActionListener(e -> newAgent());
        deleteBtn.addActionListener(e -> deleteAgent());
        JPanel listButtons = new JPanel(new FlowLayout(FlowLayout.LEFT));
        listButtons.add(newBtn);
        listButtons.add(deleteBtn);
        JPanel left = new JPanel(new BorderLayout());
        left.add(new JScrollPane(list), BorderLayout.CENTER);
        left.add(listButtons, BorderLayout.SOUTH);
        left.setPreferredSize(new Dimension(300, 400));

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, left, editor());
        add(split, BorderLayout.CENTER);
        reload(null);
    }

    void onAgentsChanged(Runnable r) {
        this.onChange = r;
    }

    private JPanel editor() {
        JPanel form = new JPanel(new GridBagLayout());
        int row = 0;
        row = field(form, row, "Id (folder name)", id);
        row = field(form, row, "Name", name);
        row = field(form, row, "Description (used by the LLM router)", description);
        JPanel flags = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        flags.add(enabled);
        flags.add(fallback);
        row = field(form, row, "", flags);
        row = field(form, row, "Keywords (word:weight, ...)", keywords);
        row = field(form, row, "Sender patterns (*@domain, ...)", patterns);
        row = field(form, row, "Sender roles (address book)", roles);
        row = field(form, row, "Sender tags (address book)", tags);
        JPanel numbers = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        numbers.add(language);
        numbers.add(new JLabel("  context tokens"));
        numbers.add(contextTokens);
        numbers.add(new JLabel("  history tokens"));
        numbers.add(historyTokens);
        numbers.add(new JLabel("  max words"));
        numbers.add(maxWords);
        row = field(form, row, "Language / budgets", numbers);
        row = field(form, row, "Never disclose (comma separated)", forbidden);
        row = field(form, row, "Model override (optional)", modelOverride);

        prompt.setLineWrap(true);
        prompt.setWrapStyleWord(true);
        prompt.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        JScrollPane promptScroll = new JScrollPane(prompt);
        promptScroll.setBorder(BorderFactory.createTitledBorder("System prompt (role, tone, rules, preferences)"));

        JPanel filesPanel = new JPanel(new BorderLayout());
        filesPanel.setBorder(BorderFactory.createTitledBorder(
                "Private context documents (pinned/ = always sent, others = retrieved when relevant)"));
        filesPanel.add(new JScrollPane(fileList), BorderLayout.CENTER);
        JButton add = new JButton("Add document...");
        JButton addPinned = new JButton("Add pinned document...");
        JButton remove = new JButton("Remove");
        add.addActionListener(e -> addFile(false));
        addPinned.addActionListener(e -> addFile(true));
        remove.addActionListener(e -> removeFile());
        JPanel fb = new JPanel(new FlowLayout(FlowLayout.LEFT));
        fb.add(add);
        fb.add(addPinned);
        fb.add(remove);
        filesPanel.add(fb, BorderLayout.SOUTH);
        filesPanel.setPreferredSize(new Dimension(400, 150));

        JButton save = new JButton("Save agent");
        save.addActionListener(e -> save());
        JPanel bottom = new JPanel(new BorderLayout());
        bottom.add(filesPanel, BorderLayout.CENTER);
        JPanel saveBar = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        saveBar.add(save);
        bottom.add(saveBar, BorderLayout.SOUTH);

        JPanel editor = new JPanel(new BorderLayout(4, 4));
        editor.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));
        editor.add(form, BorderLayout.NORTH);
        editor.add(promptScroll, BorderLayout.CENTER);
        editor.add(bottom, BorderLayout.SOUTH);
        return editor;
    }

    private static int field(JPanel form, int row, String label, Component c) {
        GridBagConstraints l = new GridBagConstraints();
        l.gridx = 0;
        l.gridy = row;
        l.anchor = GridBagConstraints.WEST;
        l.insets = new Insets(2, 2, 2, 8);
        form.add(new JLabel(label), l);
        GridBagConstraints f = new GridBagConstraints();
        f.gridx = 1;
        f.gridy = row;
        f.weightx = 1;
        f.fill = GridBagConstraints.HORIZONTAL;
        f.insets = new Insets(2, 2, 2, 2);
        form.add(c, f);
        return row + 1;
    }

    private void reload(String selectId) {
        model.clear();
        assistant.profiles().forEach(model::addElement);
        for (int i = 0; i < model.size(); i++) {
            if (model.get(i).id().equals(selectId)) {
                list.setSelectedIndex(i);
                return;
            }
        }
        if (!model.isEmpty()) {
            list.setSelectedIndex(0);
        }
    }

    private void show(AgentProfile p) {
        creating = false;
        id.setText(p.id());
        id.setEditable(false);
        name.setText(p.displayName());
        description.setText(p.description());
        enabled.setSelected(p.enabled());
        fallback.setSelected(p.fallback());
        keywords.setText(FileAgentProfileRepository.formatKeywords(p.keywords()));
        patterns.setText(String.join(", ", p.senderPatterns()));
        roles.setText(String.join(", ", p.senderRoles()));
        tags.setText(String.join(", ", p.senderTags()));
        language.setSelectedItem(p.language());
        contextTokens.setValue(p.maxContextTokens());
        historyTokens.setValue(p.maxHistoryTokens());
        maxWords.setValue(p.maxWords());
        forbidden.setText(String.join(", ", p.forbiddenTerms()));
        modelOverride.setText(p.modelOverride());
        prompt.setText(p.systemPrompt());
        prompt.setCaretPosition(0);
        listFiles(p);
    }

    private void listFiles(AgentProfile p) {
        files.clear();
        Path dir = p.contextDir();
        if (!Files.isDirectory(dir)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(dir)) {
            walk.filter(Files::isRegularFile).sorted()
                    .forEach(f -> files.addElement(dir.relativize(f).toString().replace('\\', '/')));
        } catch (IOException ignored) {
            // empty list
        }
    }

    private void newAgent() {
        list.clearSelection();
        creating = true;
        id.setText("");
        id.setEditable(true);
        name.setText("");
        description.setText("");
        enabled.setSelected(true);
        fallback.setSelected(false);
        keywords.setText("");
        patterns.setText("");
        roles.setText("");
        tags.setText("");
        language.setSelectedItem("auto");
        contextTokens.setValue(900);
        historyTokens.setValue(700);
        maxWords.setValue(250);
        forbidden.setText("");
        modelOverride.setText("");
        prompt.setText("You handle ... emails.\n\nTone: ...\nAlways: ...\nNever: ...\n");
        files.clear();
        id.requestFocus();
    }

    private void save() {
        String agentId = id.getText().trim();
        if (agentId.isEmpty()) {
            JOptionPane.showMessageDialog(this, "The id is mandatory.");
            return;
        }
        if (creating && assistant.profiles().stream().anyMatch(p -> p.id().equals(agentId))) {
            JOptionPane.showMessageDialog(this, "An agent with this id already exists.");
            return;
        }
        Path dir = assistant.config().dataDir().resolve("agents").resolve(agentId);
        AgentProfile p = new AgentProfile(agentId, name.getText().trim().isEmpty() ? agentId : name.getText().trim(),
                description.getText().trim(), "specialized", enabled.isSelected(), fallback.isSelected(),
                FileAgentProfileRepository.parseKeywords(keywords.getText()), Text.splitList(patterns.getText()),
                normalized(roles.getText()), normalized(tags.getText()), (String) language.getSelectedItem(),
                (Integer) contextTokens.getValue(), (Integer) historyTokens.getValue(), (Integer) maxWords.getValue(),
                Text.splitList(forbidden.getText()), modelOverride.getText().trim(), prompt.getText(), dir);
        try {
            assistant.saveProfile(p);
            reload(agentId);
            onChange.run();
            JOptionPane.showMessageDialog(this, "Agent '" + agentId + "' saved; the organisation was updated.");
        } catch (RuntimeException e) {
            JOptionPane.showMessageDialog(this, e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private static List<String> normalized(String s) {
        List<String> out = new ArrayList<>();
        Text.splitList(s).forEach(x -> out.add(Text.normalize(x)));
        return out;
    }

    private void deleteAgent() {
        AgentProfile p = list.getSelectedValue();
        if (p == null) {
            return;
        }
        int ok = JOptionPane.showConfirmDialog(this, "Delete agent '" + p.id() + "' and its context documents?",
                "Confirm", JOptionPane.YES_NO_OPTION);
        if (ok == JOptionPane.YES_OPTION) {
            assistant.deleteProfile(p.id());
            reload(null);
            onChange.run();
        }
    }

    private void addFile(boolean pinned) {
        AgentProfile p = list.getSelectedValue();
        if (p == null) {
            JOptionPane.showMessageDialog(this, "Save the agent first.");
            return;
        }
        JFileChooser chooser = new JFileChooser();
        chooser.setMultiSelectionEnabled(true);
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            for (File f : chooser.getSelectedFiles()) {
                assistant.addContextFile(p.id(), f.toPath(), pinned);
            }
            listFiles(p);
        }
    }

    private void removeFile() {
        AgentProfile p = list.getSelectedValue();
        String f = fileList.getSelectedValue();
        if (p == null || f == null) {
            return;
        }
        try {
            Files.deleteIfExists(p.contextDir().resolve(f));
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
        listFiles(p);
    }
}
