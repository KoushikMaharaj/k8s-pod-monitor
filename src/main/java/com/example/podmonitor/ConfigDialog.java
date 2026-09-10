package com.example.podmonitor;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;
import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class ConfigDialog extends JDialog {

    // ── Fields ────────────────────────────────────────────────────────────────
    private final JComboBox<KubeConfig.Context> cbContext;
    private final JLabel lblNoContexts;
    private final JComboBox<String> cbNamespace = new JComboBox<>();

    private final JSlider sliderInterval = new JSlider(1, 60, 5);
    private final JLabel lblInterval = new JLabel("5 min");

    private final JCheckBox chkCrash = check("CrashLoopBackOff");
    private final JCheckBox chkError = check("Error");
    private final JCheckBox chkPending = check("Pending");
    private final JCheckBox chkOOM = check("OOMKilled");
    private final JCheckBox chkTerminating = check("Terminating");

    private MonitorConfig result = null;


    // ── Constructor ───────────────────────────────────────────────────────────
    private ConfigDialog() {
        super((Frame) null, "Pod Monitor — Configure", true);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setResizable(false);

        List<KubeConfig.Context> contexts = KubeConfig.loadContexts();
        String currentCtx = KubeConfig.currentContext();

        cbContext = new JComboBox<>();
        cbContext.setFont(new Font("Monospaced", Font.PLAIN, 12));
        cbContext.setRenderer(new ContextCellRenderer());

        boolean hasContexts = !contexts.isEmpty();
        for (KubeConfig.Context ctx : contexts) {
            cbContext.addItem(ctx);
            if (ctx.name.equals(currentCtx)) {
                cbContext.setSelectedItem(ctx);
            }
        }

        lblNoContexts = new JLabel(
                "⚠  No contexts found in ~/.kube/config — check your kubeconfig.");
        lblNoContexts.setFont(new Font("SansSerif", Font.PLAIN, 11));
        lblNoContexts.setForeground(new Color(180, 80, 0));
        lblNoContexts.setVisible(!hasContexts);

        // Wire context → namespace loader
        cbContext.addActionListener(e -> onContextSelected());

        // Trigger namespace load for initial selection
        if (hasContexts) onContextSelected();

        // ── Layout ────────────────────────────────────────────────────────────
        JPanel root = new JPanel();
        root.setLayout(new BoxLayout(root, BoxLayout.Y_AXIS));
        root.setBorder(BorderFactory.createEmptyBorder(20, 22, 20, 22));
        root.setBackground(Color.WHITE);

        root.add(header());
        root.add(vgap(14));
        root.add(sep());
        root.add(vgap(14));

        root.add(sectionLabel("Kube context"));
        root.add(vgap(8));
        root.add(contextPanel());
        root.add(vgap(4));
        root.add(lblNoContexts);
        root.add(vgap(16));
        root.add(sep());
        root.add(vgap(14));

        root.add(sectionLabel("Namespace"));
        root.add(vgap(8));
        root.add(nsPanel());                  // ← was commented out, now active
        root.add(vgap(16));
        root.add(sep());
        root.add(vgap(14));

        root.add(sectionLabel("Check Interval"));
        root.add(vgap(8));
        root.add(intervalPanel());
        root.add(vgap(16));
        root.add(sep());
        root.add(vgap(14));

        root.add(sectionLabel("Alert on Pod States"));
        root.add(vgap(8));
        root.add(alertPanel());
        root.add(vgap(20));
        root.add(sep());
        root.add(vgap(14));

        root.add(buttonRow());

        setContentPane(root);
        pack();
        setMinimumSize(new Dimension(560, 0));
        setLocationRelativeTo(null);
    }

    // ── Static factory ────────────────────────────────────────────────────────
    public static MonitorConfig open() throws Exception {
        MonitorConfig[] holder = {null};

        if (SwingUtilities.isEventDispatchThread()) {
            // Already on EDT — create and show directly
            ConfigDialog dlg = new ConfigDialog();
            dlg.setVisible(true);
            holder[0] = dlg.result;
        } else {
            // Called from non-EDT thread (e.g. main)
            SwingUtilities.invokeAndWait(() -> {
                ConfigDialog dlg = new ConfigDialog();
                dlg.setVisible(true);
                holder[0] = dlg.result;
            });
        }

        return holder[0];
    }

    // ── Context selection → fetch namespaces ──────────────────────────────────
    private void onContextSelected() {
        KubeConfig.Context sel = (KubeConfig.Context) cbContext.getSelectedItem();
        if (sel == null) return;

        cbNamespace.removeAllItems();
        cbNamespace.addItem("Loading...");
        cbNamespace.setEnabled(false);

        new Thread(() -> {
            List<String> ns = fetchNamespaces(sel.name);
            SwingUtilities.invokeLater(() -> {
                cbNamespace.removeAllItems();
                if (ns.isEmpty()) {
                    cbNamespace.addItem("debezium-server");
                } else {
                    for (String n : ns) cbNamespace.addItem(n);
                    // pre-select debezium-server if present
                    for (String n : ns) {
                        if (n.equals("debezium-server")) {
                            cbNamespace.setSelectedItem("debezium-server");
                            break;
                        }
                    }
                }
                cbNamespace.setEnabled(true);
            });
        }, "ns-fetcher").start();
    }

    private List<String> fetchNamespaces(String contextName) {
        try {
            String output = ShellRunner.run(
                    "kubectl get namespaces --context=" + contextName
                            + " -o jsonpath={.items[*].metadata.name}");
            String[] parts = output.trim().split("\\s+");
            return Arrays.asList(parts);
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    // ── UI sections ───────────────────────────────────────────────────────────

    private JPanel header() {
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setOpaque(false);
        p.setAlignmentX(LEFT_ALIGNMENT);

        JLabel title = new JLabel("Pod Status Notifier");
        title.setFont(new Font("SansSerif", Font.BOLD, 16));
        title.setForeground(new Color(20, 20, 20));
        title.setAlignmentX(LEFT_ALIGNMENT);

        JLabel sub = new JLabel("Select a kube context, namespace, and polling interval");
        sub.setFont(new Font("SansSerif", Font.PLAIN, 12));
        sub.setForeground(new Color(130, 130, 130));
        sub.setAlignmentX(LEFT_ALIGNMENT);

        p.add(title);
        p.add(Box.createVerticalStrut(3));
        p.add(sub);
        return p;
    }

    private JPanel contextPanel() {
        JButton btnRefresh = new JButton("↻ Refresh");
        btnRefresh.setFont(new Font("SansSerif", Font.PLAIN, 11));
        btnRefresh.setFocusPainted(false);
        btnRefresh.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btnRefresh.setToolTipText("Reload contexts from ~/.kube/config");
        btnRefresh.addActionListener(e -> refreshContexts());

        cbContext.setBorder(normalBorder());
        cbContext.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));

        JPanel row = new JPanel(new BorderLayout(8, 0));
        row.setOpaque(false);
        row.setAlignmentX(LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
        row.add(cbContext, BorderLayout.CENTER);
        row.add(btnRefresh, BorderLayout.EAST);
        return row;
    }

    private JPanel nsPanel() {
        cbNamespace.setFont(new Font("Monospaced", Font.PLAIN, 12));
        cbNamespace.setBorder(normalBorder());
        cbNamespace.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));

        JPanel p = new JPanel(new BorderLayout());
        p.setOpaque(false);
        p.setAlignmentX(LEFT_ALIGNMENT);
        p.add(fieldGroup("Namespace *", cbNamespace), BorderLayout.CENTER);
        return p;
    }

    private JPanel intervalPanel() {
        configureSlider();

        JPanel row = new JPanel(new BorderLayout(10, 0));
        row.setOpaque(false);
        row.setAlignmentX(LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 52));
        row.add(sliderInterval, BorderLayout.CENTER);

        lblInterval.setFont(new Font("Monospaced", Font.BOLD, 13));
        lblInterval.setForeground(new Color(37, 99, 235));
        lblInterval.setPreferredSize(new Dimension(56, 20));
        lblInterval.setHorizontalAlignment(SwingConstants.RIGHT);
        row.add(lblInterval, BorderLayout.EAST);
        return row;
    }

    private void configureSlider() {
        sliderInterval.setMajorTickSpacing(15);
        sliderInterval.setMinorTickSpacing(5);
        sliderInterval.setPaintTicks(true);
        sliderInterval.setPaintLabels(true);
        sliderInterval.setOpaque(false);
        sliderInterval.setAlignmentX(LEFT_ALIGNMENT);

        java.util.Hashtable<Integer, JLabel> ticks = new java.util.Hashtable<>();
        for (int v : new int[]{1, 15, 30, 45, 60}) {
            JLabel l = new JLabel(v + "m");
            l.setFont(new Font("Monospaced", Font.PLAIN, 10));
            l.setForeground(new Color(150, 150, 150));
            ticks.put(v, l);
        }
        sliderInterval.setLabelTable(ticks);
        sliderInterval.addChangeListener(e ->
                lblInterval.setText(sliderInterval.getValue() + " min"));
    }

    private JPanel alertPanel() {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        p.setOpaque(false);
        p.setAlignmentX(LEFT_ALIGNMENT);
        for (JCheckBox cb : new JCheckBox[]{chkCrash, chkError, chkPending, chkOOM, chkTerminating}) {
            p.add(cb);
        }
        return p;
    }

    private JPanel buttonRow() {
        JButton btnCancel = new JButton("Cancel");
        stylePlainBtn(btnCancel);
        btnCancel.addActionListener(e -> dispose());

        JButton btnStart = new JButton("Start Monitoring");
        stylePrimaryBtn(btnStart);
        btnStart.addActionListener(e -> {
            if (!validateForm()) return;
            result = buildConfig();
            dispose();
        });

        JPanel p = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        p.setOpaque(false);
        p.setAlignmentX(LEFT_ALIGNMENT);
        p.add(btnCancel);
        p.add(btnStart);
        return p;
    }

    // ── Refresh ───────────────────────────────────────────────────────────────

    private void refreshContexts() {
        KubeConfig.Context prev = (KubeConfig.Context) cbContext.getSelectedItem();
        String prevName = prev != null ? prev.name : KubeConfig.currentContext();

        cbContext.removeAllItems();
        List<KubeConfig.Context> contexts = KubeConfig.loadContexts();
        for (KubeConfig.Context ctx : contexts) {
            cbContext.addItem(ctx);
            if (ctx.name.equals(prevName)) cbContext.setSelectedItem(ctx);
        }
        lblNoContexts.setVisible(contexts.isEmpty());
        pack();
    }

    // ── Validation ────────────────────────────────────────────────────────────

    private boolean validateForm() {
        boolean ok = true;

        if (cbContext.getSelectedItem() == null) {
            lblNoContexts.setVisible(true);
            ok = false;
        }

        if (cbNamespace.getSelectedItem() == null
                || cbNamespace.getSelectedItem().toString().isBlank()) {
            ok = false;
        }

        if (!ok) {
            JOptionPane.showMessageDialog(this,
                    "Please select a kube context and a namespace.",
                    "Validation error", JOptionPane.WARNING_MESSAGE);
        }
        return ok;
    }

    // ── Config builder ────────────────────────────────────────────────────────

    private MonitorConfig buildConfig() {
        KubeConfig.Context ctx = (KubeConfig.Context) cbContext.getSelectedItem();
        MonitorConfig c = new MonitorConfig();
        c.contextName = ctx.name;
        c.cluster     = ctx.cluster;
        c.namespace   = cbNamespace.getSelectedItem().toString();
        c.logDir      = System.getProperty("user.home") + File.separator + "PodStatusNotifier";
        c.intervalMs = sliderInterval.getValue() * 60_000;
        c.alertCrash = chkCrash.isSelected();
        c.alertError = chkError.isSelected();
        c.alertPending = chkPending.isSelected();
        c.alertOOM = chkOOM.isSelected();
        c.alertTerminating = chkTerminating.isSelected();
        return c;
    }

    // ── Dropdown renderer ─────────────────────────────────────────────────────

    private static class ContextCellRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value,
                                                      int index, boolean isSelected, boolean cellHasFocus) {
            super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            if (value instanceof KubeConfig.Context) {
                KubeConfig.Context ctx = (KubeConfig.Context) value;
                String clusterHint = ctx.cluster.isBlank() ? "" : "  (" + ctx.cluster + ")";
                setText("<html><b>" + ctx.name + "</b>"
                        + "<span style='color:#888'>" + clusterHint + "</span></html>");
                setFont(new Font("Monospaced", Font.PLAIN, 12));
            }
            return this;
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    // For JTextField
    private JPanel fieldGroup(String labelText, JTextField tf) {
        tf.setBorder(normalBorder());
        return fieldGroup(labelText, (JComponent) tf);
    }

    // For any JComponent (JComboBox, JPanel with browse button, etc.)
    private JPanel fieldGroup(String labelText, JComponent comp) {
        JPanel p = new JPanel(new BorderLayout(0, 4));
        p.setOpaque(false);
        JLabel l = new JLabel(labelText);
        l.setFont(new Font("SansSerif", Font.PLAIN, 11));
        l.setForeground(new Color(100, 100, 100));
        p.add(l, BorderLayout.NORTH);
        p.add(comp, BorderLayout.CENTER);
        return p;
    }

    private static JTextField monoField(String text) {
        JTextField tf = new JTextField(text);
        tf.setFont(new Font("Monospaced", Font.PLAIN, 12));
        return tf;
    }

    private static JCheckBox check(String label) {
        JCheckBox cb = new JCheckBox(label, true);
        cb.setFont(new Font("SansSerif", Font.PLAIN, 12));
        cb.setForeground(new Color(50, 50, 50));
        cb.setOpaque(false);
        cb.setFocusPainted(false);
        return cb;
    }

    private JSeparator sep() {
        JSeparator s = new JSeparator();
        s.setAlignmentX(LEFT_ALIGNMENT);
        s.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
        s.setForeground(new Color(230, 230, 230));
        return s;
    }

    private Component vgap(int px) {
        return Box.createVerticalStrut(px);
    }

    private JLabel sectionLabel(String text) {
        JLabel l = new JLabel(text);
        l.setFont(new Font("SansSerif", Font.BOLD, 12));
        l.setForeground(new Color(60, 60, 60));
        l.setAlignmentX(LEFT_ALIGNMENT);
        return l;
    }

    private Border normalBorder() {
        return BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(210, 210, 210), 1, true),
                BorderFactory.createEmptyBorder(5, 8, 5, 8));
    }

    private Border errorBorder() {
        return BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(220, 38, 38), 1, true),
                BorderFactory.createEmptyBorder(5, 8, 5, 8));
    }

    private void stylePrimaryBtn(JButton b) {
        b.setFont(new Font("SansSerif", Font.BOLD, 13));
        b.setForeground(Color.WHITE);
        b.setBackground(new Color(37, 99, 235));
        b.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(29, 78, 216), 1, true),
                BorderFactory.createEmptyBorder(9, 24, 9, 24)));
        b.setFocusPainted(false);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    }

    private void stylePlainBtn(JButton b) {
        b.setFont(new Font("SansSerif", Font.PLAIN, 13));
        b.setForeground(new Color(80, 80, 80));
        b.setBackground(Color.WHITE);
        b.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(200, 200, 200), 1, true),
                BorderFactory.createEmptyBorder(9, 20, 9, 20)));
        b.setFocusPainted(false);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    }
}