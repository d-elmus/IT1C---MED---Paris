import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.*;
import java.util.List;

public class AppWindow extends JFrame {

    // ── Composants ────────────────────────────────────────────────
    private final MapPanel mapPanel;

    private final JComboBox<String> departBox  = new JComboBox<>();
    private final JComboBox<String> arriveeBox = new JComboBox<>();
    private final JLabel            resultLabel = new JLabel(" ");

    // Bouton "Voir les détails →" + contenu HTML complet du dernier résultat
    private final JButton detailBtn  = new JButton("Voir les détails →");
    private String        detailHtml = null;

    // Station name → coordonnées (pour appel RAPTOR)
    private final Map<String, MetroLoader.StationInfo> stationMap  = new HashMap<>();
    // stop_id → [lat, lon] (pour tracer l'itinéraire sur la carte)
    private final Map<String, double[]>                stopCoords  = new HashMap<>();
    // stop_id → nom lisible (pour le détail itinéraire)
    private final Map<String, String>                  stopIdToName = new HashMap<>();

    private final JSpinner timeSpinner = new JSpinner(new javax.swing.SpinnerDateModel());
    private final JButton  btnDepartAt = new JButton("Départ à");
    private final JButton  btnArriveAt = new JButton("Arrivée à");
    private boolean        isDepartTime = true;

    // Status
    private final JLabel statusDot   = new JLabel();
    private final JLabel statusText  = new JLabel("Chargement…");
    private final JLabel statusSub   = new JLabel("Import GTFS");

    // ── Palette ───────────────────────────────────────────────────
    private static final Color BLUE        = new Color(59, 130, 246);   // #3B82F6
    private static final Color BLUE_DARK   = new Color(37, 99, 235);    // #2563EB
    private static final Color GREEN_DOT   = new Color(34, 197, 94);    // #22C55E
    private static final Color BLUE_DOT    = new Color(96, 165, 250);   // #60A5FA
    private static final Color LOGO_BG     = new Color(124, 131, 208);  // #7C83D0
    private static final Color FIELD_BG    = new Color(249, 250, 251);  // #F9FAFB
    private static final Color BORDER_CLR  = new Color(229, 231, 235);  // #E5E7EB
    private static final Color LABEL_GREY  = new Color(107, 114, 128);  // #6B7280
    private static final Color TEXT_DARK   = new Color(17, 24, 39);     // #111827

    // ── Constructeur ──────────────────────────────────────────────

    public AppWindow() {
        setTitle("Y — Itinéraires");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(1280, 800);
        setMinimumSize(new Dimension(900, 600));
        setLocationRelativeTo(null);
        setBackground(Color.WHITE);

        mapPanel = new MapPanel(Collections.emptyList());

        // JLayeredPane so the card floats over the map
        JLayeredPane layered = new JLayeredPane() {
            @Override
            public void doLayout() {
                int w = getWidth(), h = getHeight();
                // Map fills everything
                mapPanel.setBounds(0, 0, w, h);
                // Card: max 400px, s'adapte si la fenêtre est trop étroite
                Component card = getComponent(0);
                int cardW = Math.min(400, w - 48);
                int cardH = Math.min(h - 48, 780);
                int cardX = 24;
                int cardY = (h - cardH) / 2;
                card.setBounds(cardX, cardY, cardW, cardH);
            }
        };
        layered.add(mapPanel, JLayeredPane.DEFAULT_LAYER);

        JPanel card = buildCard();
        layered.add(card, JLayeredPane.PALETTE_LAYER);

        setContentPane(layered);

        mapPanel.setOnStationClick(s -> departBox.setSelectedItem(s.name));

        addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent e) { mapPanel.shutdown(); }
        });
    }

    // ── Chargement automatique : Supabase d'abord, GTFS en fallback ─

    public void startLoading() {
        statusDot.setBackground(new Color(234, 179, 8));
        statusText.setText("Connexion Supabase…");
        statusSub.setText("Base de données");

        SwingWorker<List<MetroLoader.StationInfo>, Void> worker = new SwingWorker<>() {
            String lastError = "";
            @Override
            protected List<MetroLoader.StationInfo> doInBackground() throws Exception {
                try {
                    return loadStationsFromDB();
                } catch (Exception dbEx) {
                    lastError = dbEx.getMessage();
                    System.err.println("[DB ERROR] " + dbEx.getMessage());
                    // Fallback : fichiers GTFS locaux (MetroLoader)
                    try {
                        return new MetroLoader().loadMetroStations();
                    } catch (Exception gtfsEx) {
                        throw new Exception(lastError != null ? lastError : gtfsEx.getMessage());
                    }
                }
            }
            @Override
            protected void done() {
                try {
                    List<MetroLoader.StationInfo> stations = get();
                    onStationsLoaded(stations);
                } catch (Exception e) {
                    String msg = e.getCause() != null ? e.getCause().getMessage() : e.getMessage();
                    if (msg == null) msg = "erreur inconnue";
                    statusDot.setBackground(new Color(239, 68, 68));
                    statusText.setText("Erreur connexion DB");
                    statusSub.setText(msg.length() > 55 ? msg.substring(0, 55) + "…" : msg);
                    System.err.println("[FINAL ERROR] " + msg);
                }
            }
        };
        worker.execute();
    }

    // Charge les stations depuis la table stops de Supabase
    private List<MetroLoader.StationInfo> loadStationsFromDB() throws SQLException {
        // 1. Tous les stops avec leur stop_id (pour la carte de l'itinéraire)
        String sqlAll = "SELECT stop_id, stop_name, stop_lat::float AS lat, stop_lon::float AS lon "
                      + "FROM stops WHERE stop_lat IS NOT NULL AND stop_lon IS NOT NULL";

        Map<String, double[]> latLonByName = new LinkedHashMap<>();
        stopCoords.clear();
        stopIdToName.clear();

        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sqlAll);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                String id   = rs.getString("stop_id");
                String name = fixEncoding(rs.getString("stop_name"));
                double lat  = rs.getDouble("lat");
                double lon  = rs.getDouble("lon");
                if (id != null)   { stopCoords.put(id, new double[]{lat, lon}); }
                if (id != null && name != null) stopIdToName.put(id, name);
                if (name != null) latLonByName.putIfAbsent(name, new double[]{lat, lon});
            }
        }

        // 2. Construction de la liste triée de stations
        List<MetroLoader.StationInfo> list = new ArrayList<>();
        latLonByName.forEach((name, ll) ->
            list.add(new MetroLoader.StationInfo(name, ll[0], ll[1])));
        list.sort(Comparator.comparing(s -> s.name));
        return list;
    }

    private void onStationsLoaded(List<MetroLoader.StationInfo> stations) {
        onStationsLoaded(stations, "à jour");
    }

    private void onStationsLoaded(List<MetroLoader.StationInfo> stations, String source) {
        statusDot.setBackground(GREEN_DOT);
        statusText.setText(stations.size() + " stations chargées");
        statusSub.setText(source);
        mapPanel.setStations(stations);
        populateCombos(stations);
    }

    // ── Chargement depuis dossier ─────────────────────────────────

    private void chargerDepuisDossier() {
        JFileChooser fc = new JFileChooser();
        fc.setDialogTitle("Sélectionner le dossier GTFS");
        fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        if (fc.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;

        File dossier = fc.getSelectedFile();
        statusDot.setBackground(new Color(234, 179, 8));
        statusText.setText("Chargement…");
        statusSub.setText(dossier.getName());

        SwingWorker<List<MetroLoader.StationInfo>, Void> worker = new SwingWorker<>() {
            @Override
            protected List<MetroLoader.StationInfo> doInBackground() throws Exception {
                return chargerStations(dossier);
            }
            @Override
            protected void done() {
                try {
                    onStationsLoaded(get());
                } catch (Exception ex) {
                    statusDot.setBackground(new Color(239, 68, 68));
                    statusText.setText("Erreur de lecture");
                    statusSub.setText(ex.getMessage());
                    JOptionPane.showMessageDialog(AppWindow.this,
                        "Impossible de lire le dossier GTFS :\n" + ex.getMessage(),
                        "Erreur", JOptionPane.ERROR_MESSAGE);
                }
            }
        };
        worker.execute();
    }

    private List<MetroLoader.StationInfo> chargerStations(File dossier) throws IOException {
        Set<String> metroRouteIds = new HashSet<>();
        try (BufferedReader br = reader(dossier, "routes.txt")) {
            br.readLine();
            String line;
            while ((line = br.readLine()) != null) {
                String[] f = line.split(",", -1);
                if (f.length > 5 && "1".equals(f[5].trim()))
                    metroRouteIds.add(f[0].trim());
            }
        }
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
        Map<String, String[]> allStops = new HashMap<>();
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
        stationMap.clear();
        departBox.removeAllItems();
        arriveeBox.removeAllItems();
        departBox.addItem("— Sélectionner —");
        arriveeBox.addItem("— Sélectionner —");
        for (MetroLoader.StationInfo s : stations) {
            stationMap.put(s.name, s);
            departBox.addItem(s.name);
            arriveeBox.addItem(s.name);
        }
        // Fige la largeur préférée des combos pour éviter l'expansion au chargement
        Dimension d = new Dimension(1, departBox.getPreferredSize().height);
        departBox.setPreferredSize(d);
        arriveeBox.setPreferredSize(d);
        departBox.revalidate();
        arriveeBox.revalidate();
    }

    // ── Carte flottante principale ────────────────────────────────

    private JPanel buildCard() {
        // Outer panel paints the shadow + rounded background
        JPanel shadow = new JPanel(new BorderLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                // multi-layer shadow
                for (int i = 8; i >= 1; i--) {
                    int alpha = 6 + (8 - i);
                    g2.setColor(new Color(0, 0, 0, alpha));
                    g2.fillRoundRect(i, i + 2, getWidth() - i * 2, getHeight() - i * 2, 20, 20);
                }
                // white card
                g2.setColor(Color.WHITE);
                g2.fillRoundRect(8, 8, getWidth() - 16, getHeight() - 16, 16, 16);
                g2.dispose();
            }
        };
        shadow.setOpaque(false);
        shadow.setBorder(new EmptyBorder(8, 8, 8, 8));

        JPanel inner = new JPanel() {
            @Override
            public Dimension getPreferredSize() {
                Dimension d = super.getPreferredSize();
                if (getParent() != null) d.width = getParent().getWidth();
                return d;
            }
        };
        inner.setOpaque(false);
        inner.setLayout(new BoxLayout(inner, BoxLayout.Y_AXIS));
        inner.setBorder(new EmptyBorder(20, 20, 20, 20));

        // ── Header ─────────────────────────────────────────────────
        inner.add(buildCardHeader());
        inner.add(vgap(20));

        // ── DONNÉES section ────────────────────────────────────────
        inner.add(sectionLabel("DONNÉES"));
        inner.add(vgap(8));
        inner.add(buildStatusCard());
        inner.add(vgap(8));
        inner.add(buildLoadButton());
        inner.add(vgap(18));

        inner.add(buildDivider());
        inner.add(vgap(18));

        // ── ITINÉRAIRE section ─────────────────────────────────────
        inner.add(sectionLabel("ITINÉRAIRE"));
        inner.add(vgap(10));
        inner.add(buildStationField(departBox, GREEN_DOT));
        inner.add(vgap(6));
        inner.add(buildStationField(arriveeBox, BLUE_DOT));
        inner.add(vgap(14));

        inner.add(buildTimeRow());
        inner.add(vgap(16));

        inner.add(buildSearchButton());
        inner.add(vgap(14));

        inner.add(buildResultCard());

        detailBtn.setUI(new javax.swing.plaf.basic.BasicButtonUI());
        detailBtn.setFont(new Font("Segoe UI", Font.BOLD, 11));
        detailBtn.setForeground(BLUE_DARK);
        detailBtn.setOpaque(false);
        detailBtn.setContentAreaFilled(false);
        detailBtn.setBorderPainted(false);
        detailBtn.setFocusPainted(false);
        detailBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        detailBtn.setAlignmentX(LEFT_ALIGNMENT);
        detailBtn.setVisible(false);
        detailBtn.addActionListener(e -> ouvrirDetail());
        inner.add(detailBtn);
        inner.add(vgap(18));

        inner.add(buildDivider());
        inner.add(vgap(14));

        // ── OUTILS section ─────────────────────────────────────────
        inner.add(sectionLabel("OUTILS"));
        inner.add(vgap(8));
        inner.add(creerBoutonOutil("Arbre Couvrant Minimal (Kruskal)", this::lancerKruskal));
        inner.add(vgap(6));
        inner.add(creerBoutonOutil("Vérifier la connexité (BFS)", this::lancerBFS));
        inner.add(vgap(6));
        inner.add(creerBoutonOutil("Stations PMR accessibles", this::lancerPMR));

        // Wrap inner in a scroll pane that is transparent
        JScrollPane scroll = new JScrollPane(inner);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.setBorder(null);
        scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.getVerticalScrollBar().setUnitIncrement(12);

        shadow.add(scroll, BorderLayout.CENTER);
        return shadow;
    }

    private JPanel buildCardHeader() {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        p.setOpaque(false);
        p.setAlignmentX(LEFT_ALIGNMENT);

        // Logo image (logo.png chargé depuis le classpath)
        Image logoImg = loadLogoImage();
        JPanel logo = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                // Fond arrondi blanc
                g2.setColor(Color.WHITE);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 10, 10);
                // Bordure légère
                g2.setColor(BORDER_CLR);
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 10, 10);
                // Image centrée avec marge de 4px
                if (logoImg != null) {
                    int pad = 4;
                    g2.drawImage(logoImg, pad, pad, getWidth() - pad * 2, getHeight() - pad * 2, this);
                } else {
                    // Fallback si l'image ne charge pas
                    g2.setColor(LOGO_BG);
                    g2.fillRoundRect(0, 0, getWidth(), getHeight(), 10, 10);
                    g2.setColor(Color.WHITE);
                    g2.setFont(new Font("Segoe UI", Font.BOLD, 18));
                    FontMetrics fm = g2.getFontMetrics();
                    g2.drawString("Y", (getWidth() - fm.stringWidth("Y")) / 2,
                            (getHeight() + fm.getAscent() - fm.getDescent()) / 2);
                }
                g2.dispose();
            }
        };
        logo.setPreferredSize(new Dimension(38, 38));
        logo.setOpaque(false);

        JPanel titles = new JPanel();
        titles.setOpaque(false);
        titles.setLayout(new BoxLayout(titles, BoxLayout.Y_AXIS));
        titles.setBorder(new EmptyBorder(0, 10, 0, 0));

        JLabel title = new JLabel("Itinéraires");
        title.setFont(new Font("Segoe UI", Font.BOLD, 16));
        title.setForeground(TEXT_DARK);

        JLabel sub = new JLabel("Réseau métro · Paris");
        sub.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        sub.setForeground(LABEL_GREY);

        titles.add(title);
        titles.add(sub);

        p.add(logo);
        p.add(titles);
        p.setMaximumSize(new Dimension(Integer.MAX_VALUE, 42));
        return p;
    }

    private JPanel buildStatusCard() {
        JPanel card = new JPanel(new BorderLayout(10, 0)) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(FIELD_BG);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 10, 10);
                g2.setColor(BORDER_CLR);
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 10, 10);
                g2.dispose();
            }
        };
        card.setOpaque(false);
        card.setBorder(new EmptyBorder(10, 12, 10, 12));
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 56));
        card.setAlignmentX(LEFT_ALIGNMENT);

        // Green filled circle
        statusDot.setOpaque(true);
        statusDot.setBackground(GREEN_DOT);
        statusDot.setPreferredSize(new Dimension(10, 10));
        statusDot.setBorder(null);
        // Paint as circle
        JPanel dotWrap = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(statusDot.getBackground());
                g2.fillOval(0, (getHeight() - 10) / 2, 10, 10);
                g2.dispose();
            }
        };
        dotWrap.setOpaque(false);
        dotWrap.setPreferredSize(new Dimension(10, 10));

        JPanel texts = new JPanel();
        texts.setOpaque(false);
        texts.setLayout(new BoxLayout(texts, BoxLayout.Y_AXIS));

        statusText.setFont(new Font("Segoe UI", Font.BOLD, 12));
        statusText.setForeground(TEXT_DARK);

        statusSub.setFont(new Font("Segoe UI", Font.PLAIN, 10));
        statusSub.setForeground(LABEL_GREY);

        texts.add(statusText);
        texts.add(statusSub);

        card.add(dotWrap, BorderLayout.WEST);
        card.add(texts, BorderLayout.CENTER);
        return card;
    }

    private JButton buildLoadButton() {
        JButton btn = new JButton("  Charger les stations…") {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(getBackground());
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 10, 10);
                g2.setColor(BORDER_CLR);
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 10, 10);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        btn.setUI(new javax.swing.plaf.basic.BasicButtonUI());
        btn.setBackground(Color.WHITE);
        btn.setForeground(TEXT_DARK);
        btn.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        btn.setFocusPainted(false);
        btn.setContentAreaFilled(false);
        btn.setBorderPainted(false);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
        btn.setAlignmentX(LEFT_ALIGNMENT);
        btn.setHorizontalAlignment(SwingConstants.LEFT);
        btn.addMouseListener(new MouseAdapter() {
            @Override public void mouseEntered(MouseEvent e) { btn.setBackground(FIELD_BG); btn.repaint(); }
            @Override public void mouseExited(MouseEvent e)  { btn.setBackground(Color.WHITE); btn.repaint(); }
        });
        btn.addActionListener(e -> chargerDepuisDossier());
        return btn;
    }

    private JPanel buildStationField(JComboBox<String> box, Color dotColor) {
        JPanel field = new JPanel(new BorderLayout(8, 0)) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(FIELD_BG);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 10, 10);
                g2.setColor(BORDER_CLR);
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 10, 10);
                g2.dispose();
            }
        };
        field.setOpaque(false);
        field.setBorder(new EmptyBorder(0, 12, 0, 8));
        field.setMaximumSize(new Dimension(Integer.MAX_VALUE, 42));
        field.setAlignmentX(LEFT_ALIGNMENT);

        // Colored circle dot
        JPanel dot = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int s = 12;
                int x = (getWidth() - s) / 2, y = (getHeight() - s) / 2;
                g2.setColor(Color.WHITE);
                g2.fillOval(x, y, s, s);
                g2.setColor(dotColor);
                g2.setStroke(new BasicStroke(2.5f));
                g2.drawOval(x + 1, y + 1, s - 2, s - 2);
                g2.dispose();
            }
        };
        dot.setOpaque(false);
        dot.setPreferredSize(new Dimension(18, 18));

        styleCombo(box);
        box.setBackground(new Color(0, 0, 0, 0));
        box.setOpaque(false);
        box.setBorder(null);

        field.add(dot, BorderLayout.WEST);
        field.add(box, BorderLayout.CENTER);
        return field;
    }

    private JPanel buildTimeRow() {
        JPanel row = new JPanel(new GridLayout(1, 3, 6, 0));
        row.setOpaque(false);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));
        row.setAlignmentX(LEFT_ALIGNMENT);

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

        JSpinner.DateEditor editor = new JSpinner.DateEditor(timeSpinner, "HH:mm");
        timeSpinner.setEditor(editor);
        timeSpinner.setValue(new java.util.Date());
        timeSpinner.setFont(new Font("Segoe UI", Font.BOLD, 13));
        timeSpinner.setBorder(BorderFactory.createLineBorder(BORDER_CLR));
        timeSpinner.setBackground(FIELD_BG);

        row.add(btnDepartAt);
        row.add(btnArriveAt);
        row.add(timeSpinner);
        return row;
    }

    private JButton buildSearchButton() {
        JButton btn = new JButton("Rechercher un itinéraire  →") {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(getBackground());
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 12, 12);
                g2.dispose();
                // paint text manually
                FontMetrics fm = g.getFontMetrics();
                g.setColor(getForeground());
                g.setFont(getFont());
                String txt = getText();
                int tx = (getWidth() - fm.stringWidth(txt)) / 2;
                int ty = (getHeight() + fm.getAscent() - fm.getDescent()) / 2;
                g.drawString(txt, tx, ty);
            }
        };
        btn.setUI(new javax.swing.plaf.basic.BasicButtonUI());
        btn.setBackground(BLUE);
        btn.setForeground(Color.WHITE);
        btn.setFont(new Font("Segoe UI", Font.BOLD, 13));
        btn.setFocusPainted(false);
        btn.setContentAreaFilled(false);
        btn.setBorderPainted(false);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.setMaximumSize(new Dimension(Integer.MAX_VALUE, 46));
        btn.setAlignmentX(LEFT_ALIGNMENT);
        btn.addMouseListener(new MouseAdapter() {
            @Override public void mouseEntered(MouseEvent e) { btn.setBackground(BLUE_DARK); btn.repaint(); }
            @Override public void mouseExited(MouseEvent e)  { btn.setBackground(BLUE); btn.repaint(); }
        });
        btn.addActionListener(e -> rechercher());
        return btn;
    }

    private JPanel buildResultCard() {
        JPanel card = new JPanel(new BorderLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(FIELD_BG);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 10, 10);
                g2.setColor(BORDER_CLR);
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 10, 10);
                g2.dispose();
            }
        };
        card.setOpaque(false);
        card.setBorder(new EmptyBorder(12, 14, 12, 14));
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 220));
        card.setAlignmentX(LEFT_ALIGNMENT);

        resultLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        resultLabel.setForeground(LABEL_GREY);
        resultLabel.setText("Sélectionnez un départ et une arrivée");
        resultLabel.setVerticalAlignment(SwingConstants.TOP);
        card.add(resultLabel, BorderLayout.CENTER);
        return card;
    }

    // ── Actions ───────────────────────────────────────────────────

    private void rechercher() {
        String d = (String) departBox.getSelectedItem();
        String a = (String) arriveeBox.getSelectedItem();
        if (d == null || d.startsWith("—") || a == null || a.startsWith("—")) {
            resultLabel.setText("<html><span style='color:#EF4444'>Sélectionnez un départ et une arrivée.</span></html>");
            return;
        }
        if (d.equals(a)) {
            resultLabel.setText("<html><span style='color:#EF4444'>Le départ et l'arrivée sont identiques.</span></html>");
            return;
        }
        MetroLoader.StationInfo origin = stationMap.get(d);
        MetroLoader.StationInfo dest   = stationMap.get(a);
        if (origin == null || dest == null) {
            resultLabel.setText("<html><i>Coordonnées introuvables pour ces stations.</i></html>");
            return;
        }

        // Heure du spinner en "HH:MM:SS"
        java.util.Calendar cal = java.util.Calendar.getInstance();
        cal.setTime((java.util.Date) timeSpinner.getValue());
        int spinH = cal.get(java.util.Calendar.HOUR_OF_DAY);
        int spinM = cal.get(java.util.Calendar.MINUTE);

        final String startTime;
        final String arrivalDeadline;
        if (!isDepartTime) {
            arrivalDeadline = String.format("%02d:%02d:00", spinH, spinM);
            startTime = arrivalDeadline; // utilisé seulement pour le label en cas d'erreur
        } else {
            arrivalDeadline = null;
            startTime = String.format("%02d:%02d:00", spinH, spinM);
        }

        resultLabel.setText("<html><i>Calcul en cours…</i></html>");

        double oLat = origin.lat, oLon = origin.lon;
        double dLat = dest.lat,   dLon = dest.lon;

        SwingWorker<Object[], Void> worker = new SwingWorker<>() {
            @Override
            protected Object[] doInBackground() throws Exception {
                Journey j = (arrivalDeadline != null)
                        ? findJourneyByArrival(oLat, oLon, dLat, dLon, arrivalDeadline)
                        : Calculation.findJourney(oLat, oLon, dLat, dLon, 2000, 500, startTime, 60, 120, 5, 180);
                List<double[]> route = buildFullRoute(j);
                return new Object[]{j, route};
            }
            @Override
            protected void done() {
                try {
                    Object[] result = get(60, java.util.concurrent.TimeUnit.SECONDS);
                    Journey j = (Journey) result[0];
                    @SuppressWarnings("unchecked") List<double[]> route = (List<double[]>) result[1];
                    afficherResultat(d, a, startTime, j, route, arrivalDeadline);
                } catch (java.util.concurrent.TimeoutException tex) {
                    resultLabel.setText("<html><span style='color:#EF4444'>Timeout : calcul trop long (base DB surchargée ?)</span></html>");
                } catch (Exception ex) {
                    Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                    String msg = cause.getMessage() != null ? cause.getMessage() : cause.getClass().getSimpleName();
                    System.err.println("[RAPTOR ERROR] " + msg);
                    resultLabel.setText("<html><span style='color:#EF4444'>Erreur : " + esc(msg) + "</span></html>");
                }
            }
        };
        worker.execute();
    }

    private List<double[]> buildFullRoute(Journey j) {
        if (j == null || j.destPath == null || j.destPath.isEmpty()) return Collections.emptyList();
        List<double[]> route = new ArrayList<>();
        String sql = "SELECT st.stop_id, s.stop_lat::float AS lat, s.stop_lon::float AS lon "
                   + "FROM stop_times st JOIN stops s ON s.stop_id = st.stop_id "
                   + "WHERE st.trip_id = ? ORDER BY st.stop_sequence";
        try (Connection conn = Database.getConnection()) {
            for (Leg leg : j.destPath) {
                if (leg.aPied || leg.tripId == null) {
                    addIfNew(route, stopCoords.get(leg.fromStop));
                    addIfNew(route, stopCoords.get(leg.toStop));
                } else {
                    List<String>   ids    = new ArrayList<>();
                    List<double[]> coords = new ArrayList<>();
                    try (PreparedStatement ps = conn.prepareStatement(sql)) {
                        ps.setString(1, leg.tripId);
                        try (ResultSet rs = ps.executeQuery()) {
                            while (rs.next()) {
                                ids.add(rs.getString("stop_id"));
                                coords.add(new double[]{rs.getDouble("lat"), rs.getDouble("lon")});
                            }
                        }
                    }
                    int from = -1, to = -1;
                    for (int i = 0; i < ids.size(); i++) {
                        if (leg.fromStop.equals(ids.get(i))) from = i;
                        if (leg.toStop.equals(ids.get(i)))   to   = i;
                    }
                    if (from >= 0 && to >= 0 && from <= to) {
                        for (int i = from; i <= to; i++) addIfNew(route, coords.get(i));
                    } else {
                        addIfNew(route, stopCoords.get(leg.fromStop));
                        addIfNew(route, stopCoords.get(leg.toStop));
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("[ROUTE BUILD ERROR] " + e.getMessage());
            route.clear();
            for (Leg leg : j.destPath) {
                addIfNew(route, stopCoords.get(leg.fromStop));
                addIfNew(route, stopCoords.get(leg.toStop));
            }
        }
        return route;
    }

    private Journey findJourneyByArrival(double oLat, double oLon,
                                          double dLat, double dLon,
                                          String deadline) throws Exception {
        return Calculation.findJourneyByArrival(
                oLat, oLon, dLat, dLon, 2000, 500, deadline, 120, 5, 180);
    }

    private static void addIfNew(List<double[]> list, double[] pt) {
        if (pt == null) return;
        if (!list.isEmpty()) {
            double[] last = list.get(list.size() - 1);
            if (last[0] == pt[0] && last[1] == pt[1]) return;
        }
        list.add(pt);
    }

    private void afficherResultat(String dep, String arr, String startTime, Journey j, List<double[]> route, String arrivalDeadline) {
        mapPanel.clearRoute();
        if (j.destStopId == null) {
            resultLabel.setText("<html><b>" + esc(dep) + " → " + esc(arr) + "</b>"
                    + "<br><span style='color:#EF4444'>Aucun trajet trouvé.</span>"
                    + "<br><small>Essayez un rayon plus large ou une autre heure.</small></html>");
            return;
        }
        if (!route.isEmpty()) mapPanel.setRoute(route);

        // ── Calcul durée et correspondances ───────────────────────
        // Utiliser l'heure du premier leg (départ réel) plutôt que l'heure de recherche
        String actualDep = (!j.destPath.isEmpty() && j.destPath.get(0).departTime != null)
                ? j.destPath.get(0).departTime : startTime;
        int startSec    = Calculation.toSeconds(actualDep);
        int arrSec      = Calculation.toSeconds(j.destTotalArrivalTime);
        int durationMin = Math.max(0, (arrSec - startSec) / 60);
        long transitLegs   = j.destPath.stream().filter(l -> !l.aPied).count();
        int correspondances = (int) Math.max(0, transitLegs - 1);
        String heureArr = j.destTotalArrivalTime != null ? j.destTotalArrivalTime.substring(0, 5) : "—";

        // ── Affichage des étapes ──────────────────────────────────
        StringBuilder sb = new StringBuilder("<html>");
        sb.append("<b style='font-size:16px'>").append(durationMin).append(" min</b>")
          .append("&nbsp;<span style='color:#6B7280;font-size:11px'>")
          .append(correspondances).append(" correspondance").append(correspondances > 1 ? "s" : "")
          .append("</span><br>");
        if (arrivalDeadline != null) {
            boolean tooLate = Calculation.toSeconds(j.destTotalArrivalTime) > Calculation.toSeconds(arrivalDeadline);
            if (tooLate) {
                sb.append("<span style='color:#EF4444;font-size:10px'>⚠ Arrive après ")
                  .append(arrivalDeadline, 0, 5).append(" — partez plus tôt</span><br>");
            } else {
                sb.append("<span style='color:#10B981;font-size:10px'>✓ Arrive avant ")
                  .append(arrivalDeadline, 0, 5).append("</span><br>");
            }
        }
        sb.append("<span style='color:#6B7280'>")
          .append(actualDep, 0, 5).append(" → ").append(heureArr).append("</span><br><br>");

        for (Leg leg : j.destPath) {
            if (leg.aPied) {
                sb.append("<span style='color:#9CA3AF'>&#x1F6B6; Correspondance &nbsp;")
                  .append(leg.departTime, 0, 5).append(" → ").append(leg.arriveTime, 0, 5)
                  .append("</span><br>");
            } else {
                sb.append("<span style='color:#2563EB'>&#x1F687; ")
                  .append(leg.departTime, 0, 5).append(" → ").append(leg.arriveTime, 0, 5)
                  .append("</span><br>");
            }
        }
        if (j.finalWalkSeconds > 60) {
            sb.append("<span style='color:#9CA3AF'>&#x1F6B6; +")
              .append(j.finalWalkSeconds / 60).append(" min à pied</span>");
        }

        // ── CO2 ───────────────────────────────────────────────────────
        // Vitesse moyenne metro/RER : 30 km/h → 4 gCO2/km
        // Marche : 0 gCO2. Voiture : 180 gCO2/km
        double transitKm = 0, walkKm = 0;
        for (Leg leg : j.destPath) {
            int legSec = Calculation.toSeconds(leg.arriveTime) - Calculation.toSeconds(leg.departTime);
            if (legSec < 0) legSec = 0;
            if (leg.aPied) walkKm  += legSec / 3600.0 * 5.0;   // 5 km/h à pied
            else            transitKm += legSec / 3600.0 * 30.0; // 30 km/h metro
        }
        walkKm += j.finalWalkSeconds / 3600.0 * 5.0;
        double totalKm   = transitKm + walkKm;
        double co2Metro  = transitKm * 4;    // 4 gCO2/km en transport
        double co2Voiture = totalKm * 180;   // 180 gCO2/km en voiture
        int facteur = co2Metro > 0 ? (int) Math.round(co2Voiture / co2Metro) : (int) Math.round(co2Voiture);
        sb.append("<br><span style='color:#10B981;font-size:11px'>")
          .append("&#x1F331; ").append(String.format("%.0f", co2Metro)).append(" g CO₂")
          .append(" &nbsp;|&nbsp; voiture : ").append(String.format("%.0f", co2Voiture)).append(" g")
          .append(" &nbsp;(").append(co2Metro > 0 ? facteur + "× moins polluant" : "0 émission").append(")")
          .append("</span>");

        sb.append("</html>");

        // ── Détail itinéraire complet ──────────────────────────────
        StringBuilder det = new StringBuilder(
            "<html><body style='font-family:Segoe UI,sans-serif;font-size:12px;margin:10px'>"
            + "<h2>&#x1F687; " + esc(dep) + " → " + esc(arr) + "</h2>"
            + "<p><b>" + durationMin + " min</b> &nbsp;|&nbsp; "
            + correspondances + " correspondance" + (correspondances > 1 ? "s" : "")
            + " &nbsp;|&nbsp; " + actualDep.substring(0, 5) + " → " + heureArr + "</p>");
        if (arrivalDeadline != null) {
            boolean tooLate = Calculation.toSeconds(j.destTotalArrivalTime) > Calculation.toSeconds(arrivalDeadline);
            det.append("<p>").append(tooLate
                ? "<span style='color:#EF4444'>⚠ Arrive après " + arrivalDeadline.substring(0, 5) + "</span>"
                : "<span style='color:#10B981'>✓ Arrive avant " + arrivalDeadline.substring(0, 5) + "</span>")
              .append("</p>");
        }
        det.append("<hr>");

        // Résoudre les noms de lignes via trip_id → route_name
        Map<String, String> tripToRoute = new HashMap<>();
        List<String> transitTripIds = new ArrayList<>();
        for (Leg leg : j.destPath)
            if (!leg.aPied && leg.tripId != null) transitTripIds.add(leg.tripId);
        if (!transitTripIds.isEmpty()) {
            String placeholders = String.join(",", java.util.Collections.nCopies(transitTripIds.size(), "?"));
            String routeSql = "SELECT t.trip_id, COALESCE(NULLIF(r.route_short_name,''), r.route_long_name) AS rname "
                + "FROM trips t JOIN routes r ON r.route_id = t.route_id "
                + "WHERE t.trip_id IN (" + placeholders + ")";
            try (Connection rconn = Database.getConnection();
                 PreparedStatement ps = rconn.prepareStatement(routeSql)) {
                for (int i = 0; i < transitTripIds.size(); i++) ps.setString(i + 1, transitTripIds.get(i));
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) tripToRoute.put(rs.getString("trip_id"), rs.getString("rname"));
                }
            } catch (Exception ignored) {}
        }

        for (Leg leg : j.destPath) {
            String fromName = stopIdToName.getOrDefault(leg.fromStop, leg.fromStop);
            String toName   = stopIdToName.getOrDefault(leg.toStop,   leg.toStop);
            if (leg.aPied) {
                int walkMin = (Calculation.toSeconds(leg.arriveTime) - Calculation.toSeconds(leg.departTime)) / 60;
                det.append("<p>&#x1F6B6; <b>Correspondance à pied</b> (~").append(walkMin).append(" min)<br>")
                   .append("<span style='color:#6B7280'>").append(esc(fromName))
                   .append(" → ").append(esc(toName)).append("</span></p>");
            } else {
                int legMin = (Calculation.toSeconds(leg.arriveTime) - Calculation.toSeconds(leg.departTime)) / 60;
                String routeName = leg.tripId != null
                    ? tripToRoute.getOrDefault(leg.tripId, "Ligne inconnue") : "Ligne inconnue";
                det.append("<p><b>").append(esc(routeName)).append("</b> &nbsp;&#x1F687;<br>")
                   .append("Départ : <b>").append(leg.departTime, 0, 5).append("</b> — ")
                   .append(esc(fromName)).append("<br>")
                   .append("Arrivée : <b>").append(leg.arriveTime, 0, 5).append("</b> — ")
                   .append(esc(toName)).append("<br>")
                   .append("<span style='color:#6B7280'>Durée : ").append(legMin).append(" min</span></p>");
            }
        }
        if (j.finalWalkSeconds > 60)
            det.append("<p>&#x1F6B6; Marche finale : <b>").append(j.finalWalkSeconds / 60).append(" min</b></p>");

        // ── CO2 dans le détail ────────────────────────────────────────
        det.append("<hr><h3 style='color:#10B981'>&#x1F331; Bilan CO₂</h3>")
           .append("<table border='0' cellpadding='4'>")
           .append("<tr><td><b>Transport en commun</b></td><td><b>")
           .append(String.format("%.0f", co2Metro)).append(" g CO₂</b></td>")
           .append("<td style='color:#6B7280'>(").append(String.format("%.1f", transitKm)).append(" km à 4 g/km)</td></tr>")
           .append("<tr><td><b>Équivalent voiture</b></td><td><b>")
           .append(String.format("%.0f", co2Voiture)).append(" g CO₂</b></td>")
           .append("<td style='color:#6B7280'>(").append(String.format("%.1f", totalKm)).append(" km à 180 g/km)</td></tr>")
           .append("</table>")
           .append("<p style='color:#10B981'><b>")
           .append(co2Metro > 0
               ? "Vous émettez " + facteur + "× moins de CO₂ qu'en voiture 🌍"
               : "Trajet entièrement à pied — 0 émission 🌍")
           .append("</b></p>")
           .append("<p style='color:#9CA3AF;font-size:10px'>Sources : ADEME — métro/RER 4 gCO₂/km, voiture thermique 180 gCO₂/km</p>");

        det.append("</body></html>");

        setResultat(sb.toString(), det.toString());

        // Centrer la carte entre départ et arrivée
        mapPanel.focusStation(dep);
    }

    // ── Helpers visuels ───────────────────────────────────────────

    // Affiche un résumé dans la carte + rend le bouton "Voir les détails" visible si detail != null.
    private void setResultat(String summary, String detail) {
        resultLabel.setText(summary);
        detailHtml = detail;
        detailBtn.setVisible(detail != null && !detail.isBlank());
        detailBtn.getParent().revalidate();
        detailBtn.getParent().repaint();
    }

    // Ouvre une boîte de dialogue scrollable avec le contenu HTML complet.
    private void ouvrirDetail() {
        if (detailHtml == null) return;
        JDialog dlg = new JDialog(this, "Détails", false);
        dlg.setSize(640, 560);
        dlg.setLocationRelativeTo(this);
        dlg.setLayout(new BorderLayout());

        JEditorPane ep = new JEditorPane("text/html", detailHtml);
        ep.setEditable(false);
        ep.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        ep.setBackground(Color.WHITE);
        ep.setMargin(new Insets(14, 18, 14, 18));

        JScrollPane sp = new JScrollPane(ep);
        sp.setBorder(null);
        dlg.add(sp, BorderLayout.CENTER);

        JButton close = new JButton("Fermer");
        close.addActionListener(e -> dlg.dispose());
        JPanel bot = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        bot.add(close);
        dlg.add(bot, BorderLayout.SOUTH);

        dlg.setVisible(true);
    }

    private void styleToggleBtn(JButton b, boolean selected) {
        b.setUI(new javax.swing.plaf.basic.BasicButtonUI());
        b.setFont(new Font("Segoe UI", Font.BOLD, 11));
        b.setFocusPainted(false);
        b.setOpaque(true);
        b.setContentAreaFilled(true);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        if (selected) {
            b.setBackground(BLUE);
            b.setForeground(Color.WHITE);
            b.setBorder(BorderFactory.createLineBorder(BLUE));
        } else {
            b.setBackground(Color.WHITE);
            b.setForeground(LABEL_GREY);
            b.setBorder(BorderFactory.createLineBorder(BORDER_CLR));
        }
    }

    private JButton creerBoutonOutil(String label, Runnable action) {
        JButton b = new JButton(label);
        b.setUI(new javax.swing.plaf.basic.BasicButtonUI());
        b.setBackground(FIELD_BG);
        b.setForeground(new Color(80, 80, 80));
        b.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        b.setFocusPainted(false);
        b.setOpaque(true);
        b.setContentAreaFilled(true);
        b.setBorder(BorderFactory.createLineBorder(BORDER_CLR));
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));
        b.setAlignmentX(LEFT_ALIGNMENT);
        b.addActionListener(e -> action.run());
        return b;
    }

    // ── Kruskal ───────────────────────────────────────────────────

    private void lancerKruskal() {
        setResultat("<html><i>Kruskal en cours…</i></html>", null);
        SwingWorker<String[], Void> w = new SwingWorker<>() {
            @Override protected String[] doInBackground() throws Exception { return calculerKruskal(); }
            @Override protected void done() {
                try {
                    String[] r = get(120, java.util.concurrent.TimeUnit.SECONDS);
                    setResultat(r[0], r[1]);
                } catch (java.util.concurrent.TimeoutException tex) {
                    setResultat("<html><span style='color:#EF4444'>Timeout Kruskal.</span></html>", null);
                } catch (Exception ex) {
                    Throwable c = ex.getCause() != null ? ex.getCause() : ex;
                    setResultat("<html><span style='color:#EF4444'>Erreur : " + esc(c.getMessage()) + "</span></html>", null);
                }
            }
        };
        w.execute();
    }

    private String[] calculerKruskal() throws Exception {
        // Paires d'arrêts consécutifs dans les trips (LEAD évite la self-join lourde)
        String sql =
            "SELECT DISTINCT s1.stop_id AS fid, s1.stop_lat::float AS flat, s1.stop_lon::float AS flon, "
            + "s2.stop_id AS tid, s2.stop_lat::float AS tlat, s2.stop_lon::float AS tlon "
            + "FROM (SELECT trip_id, stop_id, "
            + "      LEAD(stop_id) OVER (PARTITION BY trip_id ORDER BY stop_sequence) AS nxt "
            + "      FROM stop_times) t "
            + "JOIN stops s1 ON s1.stop_id = t.stop_id "
            + "JOIN stops s2 ON s2.stop_id = t.nxt "
            + "WHERE t.nxt IS NOT NULL AND s1.stop_lat IS NOT NULL AND s2.stop_lat IS NOT NULL";

        Map<String, Integer> idx = new LinkedHashMap<>();
        List<int[]>          edgeIdx = new ArrayList<>();
        List<Double>         edgeW   = new ArrayList<>();

        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                String fid = rs.getString("fid"), tid = rs.getString("tid");
                double flat = rs.getDouble("flat"), flon = rs.getDouble("flon");
                double tlat = rs.getDouble("tlat"), tlon = rs.getDouble("tlon");
                idx.putIfAbsent(fid, idx.size());
                idx.putIfAbsent(tid, idx.size());
                int fi = idx.get(fid), ti = idx.get(tid);
                if (fi == ti) continue;
                edgeIdx.add(new int[]{fi, ti});
                edgeW.add(Calculation.haversine(flat, flon, tlat, tlon));
            }
        }

        int n = idx.size();
        if (n == 0) return new String[]{"<html>Aucune donnée disponible.</html>", null};

        // Trier les arêtes par poids
        Integer[] order = new Integer[edgeIdx.size()];
        for (int i = 0; i < order.length; i++) order[i] = i;
        Arrays.sort(order, Comparator.comparingDouble(edgeW::get));

        // Union-Find
        int[] parent = new int[n], rnk = new int[n];
        for (int i = 0; i < n; i++) parent[i] = i;

        int mstEdges = 0;
        double totalDist = 0;
        for (int i : order) {
            int a = kFind(parent, edgeIdx.get(i)[0]);
            int b = kFind(parent, edgeIdx.get(i)[1]);
            if (a != b) {
                kUnion(parent, rnk, a, b);
                mstEdges++;
                totalDist += edgeW.get(i);
            }
        }

        // Taille de chaque composante
        Map<Integer, Integer> compSize = new HashMap<>();
        for (int i = 0; i < n; i++) compSize.merge(kFind(parent, i), 1, Integer::sum);
        List<Integer> sizes = new ArrayList<>(compSize.values());
        sizes.sort(Collections.reverseOrder());
        int components = sizes.size();

        String connexite = components == 1
            ? "<span style='color:#10B981'>✓ Réseau connexe</span>"
            : "<span style='color:#EF4444'>⚠ " + components + " composantes connexes</span>";

        String summary = "<html>"
            + "<b style='font-size:14px'>Arbre Couvrant Minimal (Kruskal)</b><br><br>"
            + "Arrêts : <b>" + n + "</b><br>"
            + "Arêtes ACM : <b>" + mstEdges + "</b><br>"
            + "Distance totale : <b>" + String.format("%.1f", totalDist / 1000) + " km</b><br><br>"
            + connexite + "</html>";

        StringBuilder det = new StringBuilder("<html><body style='font-family:Segoe UI,sans-serif;font-size:12px;margin:10px'>"
            + "<h2>Arbre Couvrant Minimal (Kruskal)</h2>"
            + "<p><b>" + n + "</b> arrêts &nbsp;|&nbsp; <b>" + mstEdges + "</b> arêtes ACM"
            + " &nbsp;|&nbsp; Distance totale : <b>" + String.format("%.2f", totalDist / 1000) + " km</b></p>"
            + "<p>" + connexite + "</p><hr>"
            + "<h3>Composantes connexes (" + components + ")</h3><table border='0' cellpadding='4'>"
            + "<tr><th align='left'>Rang</th><th align='left'>Taille (arrêts)</th><th align='left'>% du réseau</th></tr>");
        for (int i = 0; i < sizes.size(); i++) {
            det.append("<tr><td>").append(i + 1).append("</td><td>").append(sizes.get(i))
               .append("</td><td>").append(String.format("%.1f%%", 100.0 * sizes.get(i) / n))
               .append("</td></tr>");
        }
        det.append("</table></body></html>");

        return new String[]{summary, det.toString()};
    }

    private int kFind(int[] p, int x) {
        if (p[x] != x) p[x] = kFind(p, p[x]);
        return p[x];
    }

    private void kUnion(int[] p, int[] r, int a, int b) {
        if (r[a] < r[b]) { int t = a; a = b; b = t; }
        p[b] = a;
        if (r[a] == r[b]) r[a]++;
    }

    // ── BFS connexité ─────────────────────────────────────────────

    private void lancerBFS() {
        setResultat("<html><i>BFS en cours…</i></html>", null);
        SwingWorker<String[], Void> w = new SwingWorker<>() {
            @Override protected String[] doInBackground() throws Exception { return calculerBFS(); }
            @Override protected void done() {
                try {
                    String[] r = get(120, java.util.concurrent.TimeUnit.SECONDS);
                    setResultat(r[0], r[1]);
                } catch (java.util.concurrent.TimeoutException tex) {
                    setResultat("<html><span style='color:#EF4444'>Timeout BFS.</span></html>", null);
                } catch (Exception ex) {
                    Throwable c = ex.getCause() != null ? ex.getCause() : ex;
                    setResultat("<html><span style='color:#EF4444'>Erreur : " + esc(c.getMessage()) + "</span></html>", null);
                }
            }
        };
        w.execute();
    }

    private String[] calculerBFS() throws Exception {
        // Edges from consecutive stops in same trip
        String sqlTrips =
            "SELECT DISTINCT t.stop_id AS fid, t.nxt AS tid "
            + "FROM (SELECT trip_id, stop_id, "
            + "      LEAD(stop_id) OVER (PARTITION BY trip_id ORDER BY stop_sequence) AS nxt "
            + "      FROM stop_times) t "
            + "WHERE t.nxt IS NOT NULL";
        // Edges from transfers table (same-station connections, e.g. Paris Nord platforms)
        String sqlTransfers = "SELECT from_stop_id AS fid, to_stop_id AS tid FROM transfers";

        Map<String, Set<String>> adj = new HashMap<>();
        try (Connection conn = Database.getConnection()) {
            for (String sql : new String[]{sqlTrips, sqlTransfers}) {
                try (PreparedStatement ps = conn.prepareStatement(sql);
                     ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        String a = rs.getString("fid"), b = rs.getString("tid");
                        adj.computeIfAbsent(a, k -> new HashSet<>()).add(b);
                        adj.computeIfAbsent(b, k -> new HashSet<>()).add(a);
                    }
                }
            }
        }

        int total = adj.size();
        if (total == 0) return new String[]{"<html>Aucune donnée.</html>", null};

        Set<String> visited = new HashSet<>();
        List<Integer> composantes = new ArrayList<>();

        for (String start : adj.keySet()) {
            if (visited.contains(start)) continue;
            Queue<String> queue = new ArrayDeque<>();
            queue.add(start); visited.add(start);
            int size = 0;
            while (!queue.isEmpty()) {
                size++;
                for (String nb : adj.get(queue.poll()))
                    if (visited.add(nb)) queue.add(nb);
            }
            composantes.add(size);
        }

        composantes.sort(Collections.reverseOrder());
        int nbComp = composantes.size(), largest = composantes.get(0);

        String connexite = nbComp == 1
            ? "<span style='color:#10B981'>✓ Réseau entièrement connexe</span>"
            : "<span style='color:#EF4444'>⚠ Réseau non connexe — " + nbComp + " composantes</span>";

        String summary = "<html><b style='font-size:14px'>Vérification connexité (BFS)</b><br><br>"
            + "Arrêts analysés : <b>" + total + "</b><br>"
            + "Composantes connexes : <b>" + nbComp + "</b><br>"
            + "Plus grande composante : <b>" + largest + " arrêts</b> ("
            + String.format("%.1f", 100.0 * largest / total) + "%)<br><br>"
            + connexite + "<br>"
            + "<span style='color:#6B7280;font-size:10px'>";
        StringBuilder sumSb = new StringBuilder(summary);
        int show = Math.min(5, composantes.size());
        for (int i = 0; i < show; i++)
            sumSb.append("Comp. ").append(i+1).append(" : ").append(composantes.get(i)).append(" arrêts<br>");
        sumSb.append("</span></html>");

        // Détail : toutes les composantes
        StringBuilder det = new StringBuilder("<html><body style='font-family:Segoe UI,sans-serif;font-size:12px;margin:10px'>"
            + "<h2>Vérification connexité (BFS)</h2>"
            + "<p><b>" + total + "</b> arrêts analysés &nbsp;|&nbsp; <b>" + nbComp + "</b> composantes connexes</p>"
            + "<p>" + connexite + "</p><hr>"
            + "<h3>Toutes les composantes</h3>"
            + "<table border='0' cellpadding='4'>"
            + "<tr><th align='left'>Rang</th><th align='left'>Arrêts</th><th align='left'>% réseau</th></tr>");
        for (int i = 0; i < composantes.size(); i++)
            det.append("<tr><td>").append(i+1).append("</td><td>").append(composantes.get(i))
               .append("</td><td>").append(String.format("%.1f%%", 100.0 * composantes.get(i) / total))
               .append("</td></tr>");
        det.append("</table></body></html>");

        return new String[]{sumSb.toString(), det.toString()};
    }

    // ── PMR accessibles ───────────────────────────────────────────

    private void lancerPMR() {
        setResultat("<html><i>Chargement PMR…</i></html>", null);
        SwingWorker<String[], Void> w = new SwingWorker<>() {
            @Override protected String[] doInBackground() throws Exception { return calculerPMR(); }
            @Override protected void done() {
                try {
                    String[] r = get(60, java.util.concurrent.TimeUnit.SECONDS);
                    setResultat(r[0], r[1]);
                } catch (java.util.concurrent.TimeoutException tex) {
                    setResultat("<html><span style='color:#EF4444'>Timeout PMR.</span></html>", null);
                } catch (Exception ex) {
                    Throwable c = ex.getCause() != null ? ex.getCause() : ex;
                    setResultat("<html><span style='color:#EF4444'>Erreur : " + esc(c.getMessage()) + "</span></html>", null);
                }
            }
        };
        w.execute();
    }

    private String[] calculerPMR() throws Exception {
        String sql = "SELECT stop_name FROM stops "
                   + "WHERE wheelchair_boarding = 1 "
                   + "  AND stop_lat IS NOT NULL AND stop_lon IS NOT NULL "
                   + "ORDER BY stop_name";

        List<String> noms = new ArrayList<>();
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) noms.add(fixEncoding(rs.getString("stop_name")));
        }

        int total = noms.size();
        if (total == 0) return new String[]{"<html>Aucune station PMR trouvée.</html>", null};

        // Résumé : 7 premières stations
        StringBuilder sum = new StringBuilder("<html><b style='font-size:14px'>Stations PMR accessibles</b><br><br>"
            + "<b>" + total + "</b> stations accessibles PMR<br><br>"
            + "<span style='color:#6B7280;font-size:10px'>");
        int show = Math.min(7, noms.size());
        for (int i = 0; i < show; i++) sum.append("&#x267F; ").append(esc(noms.get(i))).append("<br>");
        if (total > show) sum.append("… et ").append(total - show).append(" autres");
        sum.append("</span></html>");

        // Détail : liste complète
        StringBuilder det = new StringBuilder("<html><body style='font-family:Segoe UI,sans-serif;font-size:12px;margin:10px'>"
            + "<h2>&#x267F; Stations PMR accessibles</h2>"
            + "<p><b>" + total + "</b> stations accessibles aux personnes à mobilité réduite</p><hr><ul>");
        for (String nom : noms) det.append("<li>").append(esc(nom)).append("</li>");
        det.append("</ul></body></html>");

        return new String[]{sum.toString(), det.toString()};
    }

    private JButton boutonEnAttente(String label, String tooltip) {
        JButton b = new JButton(label);
        b.setUI(new javax.swing.plaf.basic.BasicButtonUI());
        b.setBackground(FIELD_BG);
        b.setForeground(new Color(80, 80, 80));
        b.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        b.setFocusPainted(false);
        b.setOpaque(true);
        b.setContentAreaFilled(true);
        b.setBorder(BorderFactory.createLineBorder(BORDER_CLR));
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));
        b.setAlignmentX(LEFT_ALIGNMENT);
        b.setToolTipText(tooltip);
        b.addActionListener(e ->
            resultLabel.setText("<html><i>🚧 En attente — " + esc(tooltip) + ".</i></html>"));
        return b;
    }

    private JLabel sectionLabel(String text) {
        JLabel l = new JLabel(text);
        l.setFont(new Font("Segoe UI", Font.BOLD, 10));
        l.setForeground(LABEL_GREY);
        l.setAlignmentX(LEFT_ALIGNMENT);
        return l;
    }

    private Component vgap(int h) {
        return Box.createRigidArea(new Dimension(0, h));
    }

    private Component buildDivider() {
        JSeparator s = new JSeparator();
        s.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
        s.setForeground(BORDER_CLR);
        return s;
    }

    private void styleCombo(JComboBox<String> box) {
        box.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        box.setBackground(Color.WHITE);
        box.setForeground(TEXT_DARK);
        box.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
        box.setAlignmentX(LEFT_ALIGNMENT);
        box.setBorder(null);
    }

    // Corrige le mojibake : bytes UTF-8 lus comme Windows-1252 → vraie chaîne UTF-8
    private static String fixEncoding(String s) {
        if (s == null) return null;
        try {
            byte[] bytes = s.getBytes(java.nio.charset.Charset.forName("windows-1252"));
            String fixed = new String(bytes, StandardCharsets.UTF_8);
            // Vérification : si la conversion produit des caractères de remplacement, garder l'original
            return fixed.contains("�") ? s : fixed;
        } catch (Exception e) {
            return s;
        }
    }

    private static String esc(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private Image loadLogoImage() {
        try {
            java.io.InputStream in = getClass().getResourceAsStream("/logo.png");
            if (in != null) return javax.imageio.ImageIO.read(in);
        } catch (Exception ignored) {}
        return null;
    }
}
