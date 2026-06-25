import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.List;

/**
 * Interface Java construite à partir du code de l'équipe.
 * Les fonctionnalités algorithmiques (Dijkstra, Kruskal, BFS)
 * sont marquées "En attente" — elles seront branchées
 * quand l'équipe aura terminé leur implémentation.
 */
public class AppWindow extends JFrame {

    // ── Composants ────────────────────────────────────────────────
    private final MapPanel mapPanel;
    private final JLabel   badgeLabel;

    private final JComboBox<String> departBox  = new JComboBox<>();
    private final JComboBox<String> arriveeBox = new JComboBox<>();
    private final JLabel            resultLabel = new JLabel(" ");

    // Toggle heure départ / arrivée
    private final JSpinner  timeSpinner  = new JSpinner(new javax.swing.SpinnerDateModel());
    private final JButton   btnDepartAt  = new JButton("Départ à");
    private final JButton   btnArriveAt  = new JButton("Arrivée à");
    private boolean         isDepartTime = true;

    // ── Couleurs (identiques à l'original) ────────────────────────
    private static final Color BG_DARK    = new Color(22, 33, 62);
    private static final Color ACCENT     = new Color(227, 5, 28);
    private static final Color BG_SIDEBAR = new Color(247, 248, 250);
    private static final Color LABEL_GREY = new Color(130, 130, 130);

    // ── Constructeur ─────────────────────────────────────────────

    public AppWindow() {
        setTitle("Métro, Efrei, Dodo");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(1280, 800);
        setMinimumSize(new Dimension(900, 600));
        setLocationRelativeTo(null);

        mapPanel   = new MapPanel(Collections.emptyList());
        badgeLabel = new JLabel("Chargement…");

        setLayout(new BorderLayout());
        add(buildHeader(),  BorderLayout.NORTH);
        add(mapPanel,       BorderLayout.CENTER);
        add(buildSidebar(), BorderLayout.EAST);

        mapPanel.setOnStationClick(s -> departBox.setSelectedItem(s.name));

        addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent e) { mapPanel.shutdown(); }
        });
    }

    // ── Chargement automatique au démarrage (depuis MetroLoader) ──

    public void startLoading() {
        badgeLabel.setText("Chargement…");
        SwingWorker<List<MetroLoader.StationInfo>, String> worker = new SwingWorker<>() {
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
                    badgeLabel.setText("— fichier GTFS introuvable —");
                }
            }
        };
        worker.execute();
    }

    // ── Chargement depuis un dossier sélectionné par l'utilisateur ─

    private void chargerDepuisDossier() {
        JFileChooser fc = new JFileChooser();
        fc.setDialogTitle("Sélectionner le dossier GTFS");
        fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        if (fc.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;

        File dossier = fc.getSelectedFile();
        badgeLabel.setText("Chargement…");

        SwingWorker<List<MetroLoader.StationInfo>, Void> worker = new SwingWorker<>() {
            @Override
            protected List<MetroLoader.StationInfo> doInBackground() throws Exception {
                return chargerStations(dossier);
            }
            @Override
            protected void done() {
                try {
                    List<MetroLoader.StationInfo> stations = get();
                    badgeLabel.setText(stations.size() + " stations");
                    mapPanel.setStations(stations);
                    populateCombos(stations);
                    resultLabel.setText("<html><b>" + stations.size() + " stations chargées</b>"
                        + " depuis " + dossier.getName() + "</html>");
                } catch (Exception ex) {
                    badgeLabel.setText("Erreur");
                    JOptionPane.showMessageDialog(AppWindow.this,
                        "Impossible de lire le dossier GTFS :\n" + ex.getMessage(),
                        "Erreur", JOptionPane.ERROR_MESSAGE);
                }
            }
        };
        worker.execute();
    }

    /**
     * Charge la liste des stations métro depuis un dossier GTFS.
     * Reprend la logique de MetroLoader en acceptant un chemin absolu.
     */
    private List<MetroLoader.StationInfo> chargerStations(File dossier) throws IOException {
        // Étape 1 : routes métro (type 1)
        Set<String> metroRouteIds = new HashSet<>();
        try (BufferedReader br = reader(dossier, "routes.txt")) {
            br.readLine(); // header
            String line;
            while ((line = br.readLine()) != null) {
                String[] f = line.split(",", -1);
                if (f.length > 5 && "1".equals(f[5].trim()))
                    metroRouteIds.add(f[0].trim());
            }
        }

        // Étape 2 : trips correspondants
        Set<String> metroTripIds = new HashSet<>();
        try (BufferedReader br = reader(dossier, "trips.txt")) {
            br.readLine();
            String line;
            while ((line = br.readLine()) != null) {
                String[] f = line.split(",", -1);
                if (f.length > 2 && metroRouteIds.contains(f[0].trim()))
                    metroTripIds.add(f[2].trim());
            }
        }

        // Étape 3 : tous les arrêts
        Map<String, String[]> allStops = new HashMap<>(); // id → [name, lat, lon, parent]
        try (BufferedReader br = reader(dossier, "stops.txt")) {
            String header = br.readLine();
            String[] h = header.split(",", -1);
            int iId=-1, iName=-1, iLat=-1, iLon=-1, iParent=-1;
            for (int i=0;i<h.length;i++) {
                switch (h[i].trim().toLowerCase()) {
                    case "stop_id"        -> iId     = i;
                    case "stop_name"      -> iName   = i;
                    case "stop_lat"       -> iLat    = i;
                    case "stop_lon"       -> iLon    = i;
                    case "parent_station" -> iParent = i;
                }
            }
            String line;
            while ((line = br.readLine()) != null) {
                String[] f = line.split(",", -1);
                if (f.length <= Math.max(iId, Math.max(iLat, iLon))) continue;
                String latS = iLat>=0 ? f[iLat].trim() : "";
                String lonS = iLon>=0 ? f[iLon].trim() : "";
                if (latS.isEmpty() || lonS.isEmpty()) continue;
                String id     = iId>=0     ? f[iId].trim()     : "";
                String name   = iName>=0   ? f[iName].trim()   : "";
                String parent = iParent>=0 ? f[iParent].trim() : "";
                allStops.put(id, new String[]{name, latS, lonS, parent});
            }
        }

        // Étape 4 : stop_times en streaming → trouver les stations parentes
        Set<String> stationIds = new HashSet<>();
        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(new FileInputStream(new File(dossier,"stop_times.txt")),
                        StandardCharsets.UTF_8), 1<<16)) {
            br.readLine();
            String line;
            while ((line = br.readLine()) != null) {
                int c0 = line.indexOf(',');
                if (c0 < 0) continue;
                if (!metroTripIds.contains(line.substring(0, c0))) continue;
                int c1=line.indexOf(',',c0+1), c2=line.indexOf(',',c1+1), c3=line.indexOf(',',c2+1);
                if (c1<0||c2<0) continue;
                String stopId = c3<0 ? line.substring(c2+1) : line.substring(c2+1, c3);
                String[] s = allStops.get(stopId.trim());
                if (s==null) continue;
                stationIds.add(s[3].isEmpty() ? stopId.trim() : s[3]);
            }
        }

        // Étape 5 : construction liste finale, dédupliquée par nom (comme MetroLoader)
        Map<String, MetroLoader.StationInfo> byName = new LinkedHashMap<>();
        for (String id : stationIds) {
            String[] d = allStops.get(id);
            if (d==null || d[0].isEmpty()) continue;
            try {
                double lat = Double.parseDouble(d[1]);
                double lon = Double.parseDouble(d[2]);
                byName.putIfAbsent(d[0], new MetroLoader.StationInfo(d[0], lat, lon));
            } catch (NumberFormatException ignored) {}
        }
        List<MetroLoader.StationInfo> list = new ArrayList<>(byName.values());
        list.sort(Comparator.comparing(s -> s.name));
        return list;
    }

    private BufferedReader reader(File dossier, String file) throws IOException {
        return new BufferedReader(new InputStreamReader(
                new FileInputStream(new File(dossier, file)), StandardCharsets.UTF_8));
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

    // ── Header (identique à l'original) ─────────────────────────

    private JPanel buildHeader() {
        JPanel header = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 8));
        header.setBackground(BG_DARK);
        header.setPreferredSize(new Dimension(0, 52));

        JPanel logo = new JPanel() {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(ACCENT);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 8, 8);
                g2.setColor(Color.WHITE);
                g2.setFont(new Font("Segoe UI", Font.BOLD, 17));
                FontMetrics fm = g2.getFontMetrics();
                g2.drawString("M", (getWidth()-fm.stringWidth("M"))/2,
                        (getHeight()+fm.getAscent()-fm.getDescent())/2);
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

        JPanel sideTitle = new JPanel(new FlowLayout(FlowLayout.LEFT, 14, 11));
        sideTitle.setBackground(ACCENT);
        sideTitle.setPreferredSize(new Dimension(0, 44));
        JLabel t = new JLabel("ITINÉRAIRE");
        t.setFont(new Font("Segoe UI", Font.BOLD, 13));
        t.setForeground(Color.WHITE);
        sideTitle.add(t);
        sidebar.add(sideTitle, BorderLayout.NORTH);

        JPanel body = new JPanel();
        body.setBackground(BG_SIDEBAR);
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        body.setBorder(new EmptyBorder(14, 16, 14, 16));

        // ── Données GTFS ──────────────────────────────────────────
        body.add(sectionLabel("DONNÉES"));
        body.add(vgap(6));

        JButton loadBtn = new JButton("Charger les stations…");
        loadBtn.setBackground(BG_DARK);
        loadBtn.setForeground(Color.WHITE);
        loadBtn.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        loadBtn.setFocusPainted(false);
        loadBtn.setBorderPainted(false);
        loadBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        loadBtn.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
        loadBtn.setAlignmentX(LEFT_ALIGNMENT);
        loadBtn.setToolTipText("Sélectionner le dossier GTFS (routes.txt, stops.txt…)");
        loadBtn.addActionListener(e -> chargerDepuisDossier());
        body.add(loadBtn);

        body.add(vgap(16));
        body.add(separateur());
        body.add(vgap(16));

        // ── Itinéraire ─────────────────────────────────────────────
        body.add(sectionLabel("DÉPART"));
        body.add(vgap(5));
        styleCombo(departBox);
        body.add(departBox);
        body.add(vgap(5));

        body.add(vgap(5));

        body.add(sectionLabel("ARRIVÉE"));
        body.add(vgap(5));
        styleCombo(arriveeBox);
        body.add(arriveeBox);
        body.add(vgap(16));

        JSeparator sep1 = new JSeparator();
        sep1.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
        sep1.setForeground(new Color(218, 218, 218));
        body.add(sep1);
        body.add(vgap(16));

        // Sélecteur heure
        body.add(buildTimeSelector());
        body.add(vgap(16));

        // Bouton "Rechercher" (Dijkstra — en attente)
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

        // Carte résultat
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

        body.add(vgap(16));
        body.add(separateur());
        body.add(vgap(16));

        // ── Outils (en attente) ────────────────────────────────────
        body.add(sectionLabel("OUTILS"));
        body.add(vgap(8));

        body.add(boutonEnAttente("Arbre Couvrant Minimal (Kruskal)",
                "Kruskal : arbre couvrant de poids minimal"));
        body.add(vgap(8));

        body.add(boutonEnAttente("Vérifier la connexité (BFS)",
                "BFS : vérification de la connexité du réseau"));
        body.add(vgap(8));

        body.add(boutonEnAttente("Stations PMR accessibles",
                "Filtre PMR : parcours limité aux stations accessibles"));

        sidebar.add(body, BorderLayout.CENTER);
        return sidebar;
    }

    // ── Actions ───────────────────────────────────────────────────

    private void rechercher() {
        String d = (String) departBox.getSelectedItem();
        String a = (String) arriveeBox.getSelectedItem();
        if (d == null || d.startsWith("—") || a == null || a.startsWith("—")) {
            resultLabel.setText("<html>⚠ Sélectionnez un départ et une arrivée.</html>");
            return;
        }
        if (d.equals(a)) {
            resultLabel.setText("<html>⚠ Le départ et l'arrivée sont identiques.</html>");
            return;
        }
        // Algorithme Dijkstra — en attente d'implémentation par l'équipe
        resultLabel.setText("<html><b>" + esc(d) + "</b> → <b>" + esc(a) + "</b>"
                + "<br><br><i>🚧 En attente — Dijkstra en cours d'implémentation.</i></html>");
    }

    // ── Sélecteur heure ──────────────────────────────────────────

    private JPanel buildTimeSelector() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBackground(BG_SIDEBAR);
        panel.setAlignmentX(LEFT_ALIGNMENT);

        // Toggle Départ à / Arrivée à
        JPanel toggle = new JPanel(new GridLayout(1, 2, 0, 0));
        toggle.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
        toggle.setAlignmentX(LEFT_ALIGNMENT);

        styleToggleBtn(btnDepartAt, true);
        styleToggleBtn(btnArriveAt, false);

        btnDepartAt.addActionListener(e -> {
            isDepartTime = true;
            styleToggleBtn(btnDepartAt, true);
            styleToggleBtn(btnArriveAt, false);
        });
        btnArriveAt.addActionListener(e -> {
            isDepartTime = false;
            styleToggleBtn(btnDepartAt, false);
            styleToggleBtn(btnArriveAt, true);
        });

        toggle.add(btnDepartAt);
        toggle.add(btnArriveAt);
        panel.add(toggle);
        panel.add(vgap(6));

        // Spinner HH:mm
        JSpinner.DateEditor editor = new JSpinner.DateEditor(timeSpinner, "HH:mm");
        timeSpinner.setEditor(editor);
        timeSpinner.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        timeSpinner.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
        timeSpinner.setAlignmentX(LEFT_ALIGNMENT);

        // Heure par défaut = maintenant
        timeSpinner.setValue(new java.util.Date());

        panel.add(timeSpinner);
        return panel;
    }

    private void styleToggleBtn(JButton b, boolean selected) {
        b.setUI(new javax.swing.plaf.basic.BasicButtonUI());
        b.setFont(new Font("Segoe UI", Font.BOLD, 11));
        b.setFocusPainted(false);
        b.setOpaque(true);
        b.setContentAreaFilled(true);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        if (selected) {
            b.setBackground(ACCENT);
            b.setForeground(Color.WHITE);
            b.setBorder(BorderFactory.createLineBorder(ACCENT));
        } else {
            b.setBackground(Color.WHITE);
            b.setForeground(new Color(100, 100, 100));
            b.setBorder(BorderFactory.createLineBorder(new Color(210, 210, 210)));
        }
    }

    // ── Helpers ───────────────────────────────────────────────────

    private JButton boutonEnAttente(String label, String tooltipAlgo) {
        JButton b = new JButton(label);
        b.setBackground(new Color(240, 240, 242));
        b.setForeground(new Color(80, 80, 80));
        b.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        b.setFocusPainted(false);
        b.setBorderPainted(true);
        b.setBorder(BorderFactory.createLineBorder(new Color(210, 210, 215)));
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
        b.setAlignmentX(LEFT_ALIGNMENT);
        b.setToolTipText(tooltipAlgo);
        b.addActionListener(e ->
            resultLabel.setText("<html><i>🚧 En attente —<br>"
                + esc(tooltipAlgo) + ".</i></html>"));
        return b;
    }

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

    private Component separateur() {
        JSeparator s = new JSeparator();
        s.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
        s.setForeground(new Color(218, 218, 218));
        return s;
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
