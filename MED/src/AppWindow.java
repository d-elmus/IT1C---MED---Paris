import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.awt.event.*;
import java.util.*;
import java.util.List;

public class AppWindow extends JFrame {

    private final MapPanel mapPanel;
    private final JLabel badgeLabel;
    private final JComboBox<String> departBox  = new JComboBox<>();
    private final JComboBox<String> arriveeBox = new JComboBox<>();
    private final JLabel resultLabel           = new JLabel(" ");

    // Colors
    private static final Color BG_DARK    = new Color(22, 33, 62);
    private static final Color ACCENT     = new Color(227, 5, 28);
    private static final Color BG_SIDEBAR = new Color(247, 248, 250);
    private static final Color LABEL_GREY = new Color(130, 130, 130);

    public AppWindow() {
        setTitle("Métro, Efrei, Dodo");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(1280, 800);
        setMinimumSize(new Dimension(900, 600));
        setLocationRelativeTo(null);

        mapPanel  = new MapPanel(Collections.emptyList());
        badgeLabel = new JLabel("Chargement…");

        setLayout(new BorderLayout());
        add(buildHeader(),  BorderLayout.NORTH);
        add(mapPanel,       BorderLayout.CENTER);
        add(buildSidebar(), BorderLayout.EAST);

        // Click on a marker → select station in the departure box
        mapPanel.setOnStationClick(s -> departBox.setSelectedItem(s.name));

        addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent e) { mapPanel.shutdown(); }
        });
    }

    // ── Loading (runs in background, called right after setVisible) ──

    public void startLoading() {
        SwingWorker<List<MetroLoader.StationInfo>, String> worker =
                new SwingWorker<>() {
            @Override
            protected List<MetroLoader.StationInfo> doInBackground() throws Exception {
                return new MetroLoader().loadMetroStations();
            }

            @Override
            protected void done() {
                try {
                    List<MetroLoader.StationInfo> stations = get();
                    badgeLabel.setText(stations.size() + " stations");
                    mapPanel.setStations(stations);
                    populateCombos(stations);
                } catch (Exception e) {
                    badgeLabel.setText("Erreur");
                    JOptionPane.showMessageDialog(AppWindow.this,
                            "Impossible de charger les données GTFS :\n" + e.getMessage(),
                            "Erreur", JOptionPane.ERROR_MESSAGE);
                }
            }
        };
        worker.execute();
    }

    private void populateCombos(List<MetroLoader.StationInfo> stations) {
        departBox.removeAllItems();
        arriveeBox.removeAllItems();
        departBox.addItem("— Sélectionner une station —");
        arriveeBox.addItem("— Sélectionner une station —");
        for (MetroLoader.StationInfo s : stations) {
            departBox.addItem(s.name);
            arriveeBox.addItem(s.name);
        }
    }

    // ── Header ───────────────────────────────────────────────────

    private JPanel buildHeader() {
        JPanel header = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 8));
        header.setBackground(BG_DARK);
        header.setPreferredSize(new Dimension(0, 52));

        // "M" logo
        JPanel logo = new JPanel() {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(ACCENT);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 8, 8);
                g2.setColor(Color.WHITE);
                g2.setFont(new Font("Segoe UI", Font.BOLD, 17));
                FontMetrics fm = g2.getFontMetrics();
                g2.drawString("M",
                        (getWidth()  - fm.stringWidth("M")) / 2,
                        (getHeight() + fm.getAscent() - fm.getDescent()) / 2);
            }
        };
        logo.setPreferredSize(new Dimension(34, 34));
        logo.setOpaque(false);

        JLabel title = new JLabel("Métro, Efrei, Dodo");
        title.setFont(new Font("Segoe UI", Font.BOLD, 15));
        title.setForeground(Color.WHITE);

        badgeLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        badgeLabel.setForeground(new Color(138, 180, 248));

        header.add(logo);
        header.add(title);
        header.add(Box.createHorizontalStrut(16));
        header.add(badgeLabel);
        return header;
    }

    // ── Sidebar ───────────────────────────────────────────────────

    private JPanel buildSidebar() {
        JPanel sidebar = new JPanel(new BorderLayout());
        sidebar.setPreferredSize(new Dimension(295, 0));
        sidebar.setBackground(BG_SIDEBAR);

        // Red title bar
        JPanel sideTitle = new JPanel(new FlowLayout(FlowLayout.LEFT, 14, 11));
        sideTitle.setBackground(ACCENT);
        sideTitle.setPreferredSize(new Dimension(0, 44));
        JLabel t = new JLabel("ITINÉRAIRE");
        t.setFont(new Font("Segoe UI", Font.BOLD, 13));
        t.setForeground(Color.WHITE);
        sideTitle.add(t);
        sidebar.add(sideTitle, BorderLayout.NORTH);

        // Body
        JPanel body = new JPanel();
        body.setBackground(BG_SIDEBAR);
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        body.setBorder(new EmptyBorder(18, 16, 18, 16));

        body.add(sectionLabel("DÉPART"));
        body.add(vgap(5));
        styleCombo(departBox);
        body.add(departBox);
        body.add(vgap(5));

        // Swap button row
        JPanel swapRow = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
        swapRow.setBackground(BG_SIDEBAR);
        swapRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
        JButton swapBtn = new JButton("⇅");
        swapBtn.setFont(new Font("Segoe UI", Font.PLAIN, 15));
        swapBtn.setPreferredSize(new Dimension(32, 32));
        swapBtn.setForeground(new Color(100, 100, 100));
        swapBtn.setBackground(Color.WHITE);
        swapBtn.setBorder(BorderFactory.createLineBorder(new Color(210, 210, 210)));
        swapBtn.setFocusPainted(false);
        swapBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        swapBtn.setToolTipText("Inverser départ / arrivée");
        swapBtn.addActionListener(e -> {
            int d = departBox.getSelectedIndex();
            int a = arriveeBox.getSelectedIndex();
            departBox.setSelectedIndex(a);
            arriveeBox.setSelectedIndex(d);
        });
        swapRow.add(swapBtn);
        body.add(swapRow);
        body.add(vgap(5));

        body.add(sectionLabel("ARRIVÉE"));
        body.add(vgap(5));
        styleCombo(arriveeBox);
        body.add(arriveeBox);
        body.add(vgap(16));

        JSeparator sep = new JSeparator();
        sep.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
        sep.setForeground(new Color(218, 218, 218));
        body.add(sep);
        body.add(vgap(16));

        // Search button
        JButton searchBtn = new JButton("Rechercher un itinéraire");
        searchBtn.setBackground(ACCENT);
        searchBtn.setForeground(Color.WHITE);
        searchBtn.setFont(new Font("Segoe UI", Font.BOLD, 13));
        searchBtn.setFocusPainted(false);
        searchBtn.setBorderPainted(false);
        searchBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        searchBtn.setMaximumSize(new Dimension(Integer.MAX_VALUE, 42));
        searchBtn.setAlignmentX(LEFT_ALIGNMENT);
        searchBtn.addMouseListener(new MouseAdapter() {
            @Override public void mouseEntered(MouseEvent e) { searchBtn.setBackground(ACCENT.darker()); }
            @Override public void mouseExited(MouseEvent e)  { searchBtn.setBackground(ACCENT); }
        });
        searchBtn.addActionListener(e -> rechercher());
        body.add(searchBtn);
        body.add(vgap(14));

        // Result card
        JPanel card = new JPanel(new BorderLayout());
        card.setBackground(Color.WHITE);
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 110));
        card.setAlignmentX(LEFT_ALIGNMENT);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(218, 218, 218)),
                new EmptyBorder(11, 11, 11, 11)));
        resultLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        resultLabel.setForeground(new Color(70, 70, 70));
        resultLabel.setVerticalAlignment(SwingConstants.TOP);
        card.add(resultLabel, BorderLayout.CENTER);
        body.add(card);

        // Focus button — center map on selected departure
        body.add(vgap(10));
        JButton focusBtn = new JButton("Centrer la carte sur le départ");
        focusBtn.setBackground(BG_DARK);
        focusBtn.setForeground(Color.WHITE);
        focusBtn.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        focusBtn.setFocusPainted(false);
        focusBtn.setBorderPainted(false);
        focusBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        focusBtn.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
        focusBtn.setAlignmentX(LEFT_ALIGNMENT);
        focusBtn.addActionListener(e -> {
            String sel = (String) departBox.getSelectedItem();
            if (sel != null && !sel.startsWith("—")) mapPanel.focusStation(sel);
        });
        body.add(focusBtn);

        sidebar.add(body, BorderLayout.CENTER);
        return sidebar;
    }

    // ── Actions ───────────────────────────────────────────────────

    private void rechercher() {
        String d = (String) departBox.getSelectedItem();
        String a = (String) arriveeBox.getSelectedItem();
        if (d == null || d.startsWith("—") || a == null || a.startsWith("—")) {
            resultLabel.setText("<html>⚠ Sélectionnez un départ et une arrivée.</html>");
        } else if (d.equals(a)) {
            resultLabel.setText("<html>⚠ Le départ et l'arrivée sont identiques.</html>");
        } else {
            resultLabel.setText("<html><b>" + esc(d) + "</b>"
                    + " &rarr; <b>" + esc(a) + "</b><br><br>"
                    + "<i>🚧 Fonctionnalité à venir —<br>Dijkstra branché à l'étape suivante.</i></html>");
        }
    }

    // ── Helpers ───────────────────────────────────────────────────

    private JLabel sectionLabel(String text) {
        JLabel l = new JLabel(text);
        l.setFont(new Font("Segoe UI", Font.BOLD, 11));
        l.setForeground(LABEL_GREY);
        l.setAlignmentX(LEFT_ALIGNMENT);
        return l;
    }

    private Component vgap(int h) {
        return Box.createRigidArea(new Dimension(0, h));
    }

    private void styleCombo(JComboBox<String> box) {
        box.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        box.setBackground(Color.WHITE);
        box.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
        box.setAlignmentX(LEFT_ALIGNMENT);
    }

    private static String esc(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
