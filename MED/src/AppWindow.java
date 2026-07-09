import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLEncoder;
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

    // Bouton principal de recherche (désactivé pendant le calcul, anti double-clic)
    private JButton searchBtn;
    private static final String SEARCH_LABEL = "Rechercher un itinéraire  →";

    // Station name → coordonnées (pour appel RAPTOR)
    private final Map<String, MetroLoader.StationInfo> stationMap  = new HashMap<>();
    // stop_id → [lat, lon] (pour tracer l'itinéraire sur la carte)
    private final Map<String, double[]>                stopCoords  = new HashMap<>();
    // stop_id → nom lisible (pour le détail itinéraire)
    private final Map<String, String>                  stopIdToName = new HashMap<>();

    private final JSpinner timeSpinner = new JSpinner(new javax.swing.SpinnerDateModel());
    private final JSpinner dateSpinner = new JSpinner(new javax.swing.SpinnerDateModel());
    private final JButton  btnDepartAt = new JButton("Départ à");
    private final JButton  btnArriveAt = new JButton("Arrivée à");
    private boolean        isDepartTime = true;

    // ── Paramètres de recherche (section PARAMÈTRES) ──────────────
    private final JSpinner spnRayonDepart  = new JSpinner(new SpinnerNumberModel(2000, 100, 5000, 100));
    private final JSpinner spnRayonArrivee = new JSpinner(new SpinnerNumberModel(500, 100, 5000, 100));
    private final JSpinner spnRayonMax     = new JSpinner(new SpinnerNumberModel(4000, 500, 8000, 250));
    private final JSpinner spnCorrespMin   = new JSpinner(new SpinnerNumberModel(120, 0, 600, 30));
    private final JSpinner spnMaxCorresp   = new JSpinner(new SpinnerNumberModel(5, 0, 10, 1));
    private final JSpinner spnFenetre      = new JSpinner(new SpinnerNumberModel(60, 10, 240, 10));
    private final JSpinner spnHorizon      = new JSpinner(new SpinnerNumberModel(180, 30, 360, 15));
    private final JCheckBox chkAutoRayon   = new JCheckBox("Extension automatique du rayon", true);
    private final JCheckBox chkFiltreDate  = new JCheckBox("Filtrer les horaires par date", true);

    // Points personnalisés : adresse géocodée ou point choisi sur la carte.
    // Prioritaires sur la station du menu quand le texte du champ correspond.
    private double[] customDepart  = null;
    private double[] customArrivee = null;
    private final Map<String, double[]> geocodeCache = new java.util.concurrent.ConcurrentHashMap<>();
    private static final String POINT_CARTE = "Point carte";

    // ── Autocomplétion ────────────────────────────────────────────
    private final List<String> allStationNames = new ArrayList<>();
    private final Map<String, double[]> suggestionCoords = new java.util.concurrent.ConcurrentHashMap<>();
    private boolean suppressAuto = false;
    private static final String ADRESSE_PREFIX = "Adresse : ";

    // ── Alternatives multi-objectifs ──────────────────────────────
    // Dérivées du même résultat RAPTOR (arrivalTimes + legs), sans relancer l'algorithme.
    private static class Alternative {
        final String label;
        final String tooltip;
        final Journey journey;
        final List<MapPanel.RouteSeg> segs;
        final Map<String, String[]> tripInfo;
        final int initialWalkSec;   // marche du point de départ au premier arrêt

        Alternative(String label, String tooltip, Journey journey,
                List<MapPanel.RouteSeg> segs, Map<String, String[]> tripInfo,
                int initialWalkSec) {
            this.label    = label;
            this.tooltip  = tooltip;
            this.journey  = journey;
            this.segs     = segs;
            this.tripInfo = tripInfo;
            this.initialWalkSec = initialWalkSec;
        }
    }

    private final JPanel altPanel = new JPanel(new GridLayout(0, 2, 6, 6));
    private List<Alternative> currentAlts = new ArrayList<>();
    private int currentAltIdx = 0;
    // Contexte du dernier résultat (pour re-rendre lors d'un changement d'alternative)
    private String ctxDep, ctxArr, ctxStart, ctxDeadline;
    private Boolean ctxCal;
    private java.time.LocalDate ctxDate;

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

        // Bornage du sélecteur de date : uniquement la période couverte par le feed GTFS.
        new SwingWorker<java.util.Date[], Void>() {
            @Override protected java.util.Date[] doInBackground() { return loadCalendarRange(); }
            @Override protected void done() {
                try {
                    java.util.Date[] r = get();
                    if (r != null) applyDateBounds(r[0], r[1]);
                } catch (Exception ignored) {}
            }
        }.execute();
    }

    // Période couverte par le feed GTFS (calendar + calendar_dates). null si indisponible.
    private static java.util.Date[] loadCalendarRange() {
        String sql = "SELECT LEAST((SELECT MIN(start_date) FROM calendar), (SELECT MIN(date) FROM calendar_dates)) AS dmin, "
                   + "GREATEST((SELECT MAX(end_date) FROM calendar), (SELECT MAX(date) FROM calendar_dates)) AS dmax";
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                java.sql.Date dmin = rs.getDate("dmin");
                java.sql.Date dmax = rs.getDate("dmax");
                if (dmin != null && dmax != null) {
                    return new java.util.Date[]{dmin, dmax};
                }
            }
        } catch (SQLException e) {
            System.err.println("Erreur SQL (loadCalendarRange) : " + e.getMessage());
        }
        return null;
    }

    // Restreint le sélecteur de date à [min, max] et y ramène la valeur courante si besoin.
    private void applyDateBounds(java.util.Date min, java.util.Date max) {
        java.util.Date now = new java.util.Date();
        java.util.Date init = now.before(min) ? min : (now.after(max) ? max : now);
        dateSpinner.setModel(new javax.swing.SpinnerDateModel(init, min, max, java.util.Calendar.DAY_OF_MONTH));
        JSpinner.DateEditor editor = new JSpinner.DateEditor(dateSpinner, "dd/MM/yyyy");
        dateSpinner.setEditor(editor);
        java.text.SimpleDateFormat fmt = new java.text.SimpleDateFormat("dd/MM/yyyy");
        dateSpinner.setToolTipText("Services GTFS disponibles du " + fmt.format(min)
                + " au " + fmt.format(max));
    }

    // Charge les stations depuis la table stops de Supabase
    private List<MetroLoader.StationInfo> loadStationsFromDB() throws SQLException {
        // 1. Tous les stops avec leur stop_id (pour la carte de l'itinéraire)
        String sqlAll = "SELECT stop_id, stop_name, stop_lat::float AS lat, stop_lon::float AS lon "
                      + "FROM unique_parent_station WHERE stop_lat IS NOT NULL AND stop_lon IS NOT NULL";

        String sqlAll2 = "SELECT stop_id, stop_name " + "FROM stops";

        // Requête complémentaire : stations dont au moins un quai est accessible PMR
        // (le flag wheelchair_boarding est porté par les quais enfants, pas par les parents).
        String sqlPmr = "SELECT DISTINCT COALESCE(NULLIF(parent_station,''), stop_id) AS sid "
                      + "FROM stops WHERE wheelchair_boarding = 1";

        Map<String, double[]> latLonByName = new LinkedHashMap<>();
        Set<String> pmrIds   = new HashSet<>();
        Set<String> pmrNames = new HashSet<>();
        stopCoords.clear();
        stopIdToName.clear();

        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sqlAll);
             PreparedStatement ps2 = conn.prepareStatement(sqlAll2);
             PreparedStatement ps3 = conn.prepareStatement(sqlPmr);
             ResultSet rs3 = ps3.executeQuery();
             ResultSet rs = ps.executeQuery();
             ResultSet rs2 = ps2.executeQuery()) {
            while (rs3.next()) {
                pmrIds.add(rs3.getString("sid"));
            }
            while (rs.next()) {
                String id   = rs.getString("stop_id");
                String name = fixEncoding(rs.getString("stop_name"));
                double lat  = rs.getDouble("lat");
                double lon  = rs.getDouble("lon");
                if (id != null)   { stopCoords.put(id, new double[]{lat, lon}); }
                if (id != null && name != null) stopIdToName.put(id, name);
                if (name != null) latLonByName.putIfAbsent(name, new double[]{lat, lon});
                if (id != null && name != null && pmrIds.contains(id)) pmrNames.add(name);
            }
            while (rs2.next()) {
                String id2   = rs2.getString("stop_id");
                String name2 = fixEncoding(rs2.getString("stop_name"));
                if(id2 != null && name2 != null) stopIdToName.put(id2, name2);
            }
        }

        // 2. Construction de la liste triée de stations
        List<MetroLoader.StationInfo> list = new ArrayList<>();
        latLonByName.forEach((name, ll) ->
            list.add(new MetroLoader.StationInfo(name, ll[0], ll[1], pmrNames.contains(name))));
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
        suppressAuto = true;
        try {
            stationMap.clear();
            allStationNames.clear();
            departBox.removeAllItems();
            arriveeBox.removeAllItems();
            departBox.addItem("— Sélectionner —");
            arriveeBox.addItem("— Sélectionner —");
            for (MetroLoader.StationInfo s : stations) {
                stationMap.put(s.name, s);
                allStationNames.add(s.name);
                departBox.addItem(s.name);
                arriveeBox.addItem(s.name);
            }
        } finally {
            suppressAuto = false;
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
        inner.add(buildStationField(departBox, GREEN_DOT, true));
        inner.add(vgap(6));
        inner.add(buildStationField(arriveeBox, BLUE_DOT, false));
        inner.add(vgap(4));
        inner.add(buildSwapRow());
        inner.add(vgap(10));

        inner.add(buildTimeRow());
        inner.add(vgap(6));
        inner.add(buildDateRow());
        inner.add(vgap(16));

        inner.add(buildSearchButton());
        inner.add(vgap(6));

        // Barre des alternatives multi-objectifs (vide tant qu'aucun résultat)
        altPanel.setOpaque(false);
        altPanel.setAlignmentX(LEFT_ALIGNMENT);
        altPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 0));
        inner.add(altPanel);
        inner.add(vgap(8));

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

        // ── PARAMÈTRES section ─────────────────────────────────────
        inner.add(sectionLabel("PARAMÈTRES"));
        inner.add(vgap(8));
        inner.add(buildParamsPanel());
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

    // Champ depart/arrivee : menu des stations (editable : on peut y taper une adresse)
    // + bouton "Carte" pour choisir le point directement sur la carte.
    private JPanel buildStationField(JComboBox<String> box, Color dotColor, boolean isDepart) {
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

        // Editable : permet de taper une adresse libre (geocodee a la recherche).
        box.setEditable(true);
        Component edComp = box.getEditor().getEditorComponent();
        if (edComp instanceof JTextField ed) {
            ed.setOpaque(false);
            ed.setBorder(null);
            ed.setFont(new Font("Segoe UI", Font.PLAIN, 12));
            ed.setForeground(TEXT_DARK);
        }
        // Autocomplétion : stations filtrées pendant la frappe + suggestions d'adresses.
        installAutocomplete(box);
        // Une station choisie dans la liste remplace le point personnalise.
        box.addActionListener(e -> {
            Object sel = box.getSelectedItem();
            if (sel != null && stationMap.containsKey(sel.toString())) {
                if (isDepart) customDepart = null; else customArrivee = null;
                mapPanel.setPointMarkers(customDepart, customArrivee);
            }
        });

        // Bouton "Carte" : le prochain clic sur la carte definit ce point.
        JButton pick = new JButton("Carte");
        pick.setUI(new javax.swing.plaf.basic.BasicButtonUI());
        pick.setFont(new Font("Segoe UI", Font.BOLD, 10));
        pick.setForeground(LABEL_GREY);
        pick.setBackground(Color.WHITE);
        pick.setOpaque(true);
        pick.setContentAreaFilled(true);
        pick.setFocusPainted(false);
        pick.setBorder(BorderFactory.createLineBorder(BORDER_CLR));
        pick.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        pick.setPreferredSize(new Dimension(52, 26));
        pick.setToolTipText("Choisir ce point directement sur la carte");
        pick.addActionListener(e -> mapPanel.startPointPick(pt -> {
            String label = String.format("%s (%.5f, %.5f)", POINT_CARTE, pt[0], pt[1]);
            if (isDepart) { customDepart = pt; departBox.setSelectedItem(label); }
            else          { customArrivee = pt; arriveeBox.setSelectedItem(label); }
            mapPanel.setPointMarkers(customDepart, customArrivee);
        }));

        field.add(dot, BorderLayout.WEST);
        field.add(box, BorderLayout.CENTER);
        field.add(pick, BorderLayout.EAST);
        return field;
    }

    // Petit lien "Inverser" sous les deux champs : echange depart et arrivee
    // (textes, points personnalises et marqueurs).
    private JPanel buildSwapRow() {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        row.setOpaque(false);
        row.setAlignmentX(LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 18));

        JButton swap = new JButton("Inverser départ / arrivée");
        swap.setUI(new javax.swing.plaf.basic.BasicButtonUI());
        swap.setFont(new Font("Segoe UI", Font.BOLD, 10));
        swap.setForeground(BLUE_DARK);
        swap.setOpaque(false);
        swap.setContentAreaFilled(false);
        swap.setBorderPainted(false);
        swap.setFocusPainted(false);
        swap.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        swap.addActionListener(e -> inverserDepartArrivee());
        row.add(swap);
        return row;
    }

    private void inverserDepartArrivee() {
        suppressAuto = true;
        try {
            double[] oldDep = customDepart;
            double[] oldArr = customArrivee;
            Object dSel = departBox.getSelectedItem();
            Object aSel = arriveeBox.getSelectedItem();
            departBox.setSelectedItem(aSel == null ? "" : aSel.toString());
            arriveeBox.setSelectedItem(dSel == null ? "" : dSel.toString());
            // Apres les setSelectedItem (les listeners peuvent remettre les points a null)
            customDepart  = oldArr;
            customArrivee = oldDep;
            mapPanel.setPointMarkers(customDepart, customArrivee);
        } finally {
            suppressAuto = false;
        }
    }

    // Ligne "Date du trajet" : la date sert au filtre calendrier (services actifs ce jour-la).
    private JPanel buildDateRow() {
        JPanel row = new JPanel(new GridLayout(1, 2, 6, 0));
        row.setOpaque(false);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));
        row.setAlignmentX(LEFT_ALIGNMENT);

        JLabel lbl = new JLabel("Date du trajet");
        lbl.setFont(new Font("Segoe UI", Font.BOLD, 11));
        lbl.setForeground(LABEL_GREY);

        JSpinner.DateEditor editor = new JSpinner.DateEditor(dateSpinner, "dd/MM/yyyy");
        dateSpinner.setEditor(editor);
        dateSpinner.setValue(new java.util.Date());
        dateSpinner.setFont(new Font("Segoe UI", Font.BOLD, 13));
        dateSpinner.setBorder(BorderFactory.createLineBorder(BORDER_CLR));
        dateSpinner.setBackground(FIELD_BG);

        row.add(lbl);
        row.add(dateSpinner);
        return row;
    }

    // Section PARAMÈTRES : reglages de la recherche (rayons, correspondances, horizon)
    // et options (extension auto du rayon, filtre calendrier).
    private JPanel buildParamsPanel() {
        JPanel panel = new JPanel();
        panel.setOpaque(false);
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setAlignmentX(LEFT_ALIGNMENT);

        JPanel grid = new JPanel(new GridLayout(0, 2, 8, 5));
        grid.setOpaque(false);
        grid.setAlignmentX(LEFT_ALIGNMENT);
        addParamRow(grid, "Rayon départ (m)",        spnRayonDepart);
        addParamRow(grid, "Rayon arrivée (m)",       spnRayonArrivee);
        addParamRow(grid, "Rayon maximal auto (m)",  spnRayonMax);
        addParamRow(grid, "Correspondance min. (s)", spnCorrespMin);
        addParamRow(grid, "Correspondances max.",    spnMaxCorresp);
        addParamRow(grid, "Fenêtre de départ (min)", spnFenetre);
        addParamRow(grid, "Horizon de recherche (min)", spnHorizon);
        grid.setMaximumSize(new Dimension(Integer.MAX_VALUE, 7 * 27 + 6 * 5));
        panel.add(grid);
        panel.add(vgap(8));

        styleCheck(chkAutoRayon,
            "Si aucun arrêt n'est trouvé dans le rayon, il augmente par paliers jusqu'au rayon maximal");
        styleCheck(chkFiltreDate,
            "N'utilise que les trips dont le service circule à la date choisie (calendar / calendar_dates)");
        panel.add(chkAutoRayon);
        panel.add(chkFiltreDate);
        return panel;
    }

    private void addParamRow(JPanel grid, String label, JSpinner spinner) {
        JLabel l = new JLabel(label);
        l.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        l.setForeground(LABEL_GREY);
        spinner.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        spinner.setBorder(BorderFactory.createLineBorder(BORDER_CLR));
        grid.add(l);
        grid.add(spinner);
    }

    private void styleCheck(JCheckBox c, String tooltip) {
        c.setOpaque(false);
        c.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        c.setForeground(TEXT_DARK);
        c.setFocusPainted(false);
        c.setAlignmentX(LEFT_ALIGNMENT);
        c.setToolTipText(tooltip);
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
        JButton btn = new JButton(SEARCH_LABEL) {
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
        searchBtn = btn;
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
        String d = departBox.getSelectedItem() != null ? departBox.getSelectedItem().toString().trim() : "";
        String a = arriveeBox.getSelectedItem() != null ? arriveeBox.getSelectedItem().toString().trim() : "";
        if (d.isEmpty() || d.startsWith("—") || a.isEmpty() || a.startsWith("—")) {
            resultLabel.setText("<html><span style='color:#EF4444'>Sélectionnez un départ et une arrivée (station, adresse ou point carte).</span></html>");
            return;
        }
        if (d.equals(a)) {
            resultLabel.setText("<html><span style='color:#EF4444'>Le départ et l'arrivée sont identiques.</span></html>");
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

        // Date du trajet (filtre calendrier)
        java.util.Calendar dc = java.util.Calendar.getInstance();
        dc.setTime((java.util.Date) dateSpinner.getValue());
        final java.time.LocalDate travelDate = java.time.LocalDate.of(
                dc.get(java.util.Calendar.YEAR),
                dc.get(java.util.Calendar.MONTH) + 1,
                dc.get(java.util.Calendar.DAY_OF_MONTH));
        final boolean filtreDate = chkFiltreDate.isSelected();

        // Paramètres de recherche (section PARAMÈTRES)
        final double rayonDepart  = ((Number) spnRayonDepart.getValue()).doubleValue();
        final double rayonArrivee = ((Number) spnRayonArrivee.getValue()).doubleValue();
        final double rayonMax     = ((Number) spnRayonMax.getValue()).doubleValue();
        final boolean autoRayon   = chkAutoRayon.isSelected();
        final double maxRayonDepart  = autoRayon ? Math.max(rayonMax, rayonDepart)  : rayonDepart;
        final double maxRayonArrivee = autoRayon ? Math.max(rayonMax, rayonArrivee) : rayonArrivee;
        final int correspMin = ((Number) spnCorrespMin.getValue()).intValue();
        final int maxCorresp = ((Number) spnMaxCorresp.getValue()).intValue();
        final int fenetre    = ((Number) spnFenetre.getValue()).intValue();
        final int horizon    = ((Number) spnHorizon.getValue()).intValue();

        final double[] customO = customDepart;
        final double[] customA = customArrivee;

        resultLabel.setText("<html><i>Calcul en cours…</i></html>");
        // Bouton désactivé le temps du calcul (évite les recherches en double)
        searchBtn.setEnabled(false);
        searchBtn.setText("Recherche en cours…");
        searchBtn.setBackground(new Color(147, 197, 253));
        searchBtn.repaint();

        SwingWorker<Object[], Void> worker = new SwingWorker<>() {
            @Override
            protected Object[] doInBackground() throws Exception {
                // Résolution des extrémités : point carte > station connue > adresse géocodée.
                double[] o = resolveEndpoint(d, customO, "départ");
                double[] t = resolveEndpoint(a, customA, "arrivée");

                // Services actifs à la date choisie (null = filtre indisponible ou désactivé).
                Set<String> services = filtreDate ? Calculation.getActiveServiceIds(travelDate) : null;
                Boolean calendarState = filtreDate ? Boolean.valueOf(services != null) : null;

                Journey j = (arrivalDeadline != null)
                        ? Calculation.findJourneyByArrivalEx(o[0], o[1], t[0], t[1],
                                rayonDepart, rayonArrivee, arrivalDeadline,
                                correspMin, maxCorresp, horizon,
                                services, maxRayonDepart, maxRayonArrivee)
                        : Calculation.findJourneyEx(o[0], o[1], t[0], t[1],
                                rayonDepart, rayonArrivee, startTime, fenetre,
                                correspMin, maxCorresp, horizon,
                                services, maxRayonDepart, maxRayonArrivee);

                Map<String, String[]> tripInfo = loadTripInfo(j);
                List<MapPanel.RouteSeg> segs = buildRouteSegments(j, o, t, tripInfo);

                // Alternatives multi-objectifs (mode "Départ à" uniquement : le RAPTOR
                // inverse n'expose pas les legs nécessaires à la reconstruction).
                List<Alternative> alts = (arrivalDeadline == null && j.destStopId != null)
                        ? computeAlternatives(j, o, t, rayonArrivee, maxRayonArrivee,
                                startTime, segs, tripInfo)
                        : new ArrayList<>();
                int initialWalkSec = computeInitialWalkSec(j, o);
                return new Object[]{j, segs, tripInfo, calendarState, o, t, alts, initialWalkSec};
            }
            @Override
            protected void done() {
                try {
                    Object[] result = get(60, java.util.concurrent.TimeUnit.SECONDS);
                    Journey j = (Journey) result[0];
                    @SuppressWarnings("unchecked") List<MapPanel.RouteSeg> segs = (List<MapPanel.RouteSeg>) result[1];
                    @SuppressWarnings("unchecked") Map<String, String[]> tripInfo = (Map<String, String[]>) result[2];
                    @SuppressWarnings("unchecked") List<Alternative> alts = (List<Alternative>) result[6];
                    // Marqueurs : uniquement pour les points hors station (adresse ou point carte).
                    double[] oMark = stationMap.containsKey(d) ? null : (double[]) result[4];
                    double[] tMark = stationMap.containsKey(a) ? null : (double[]) result[5];
                    mapPanel.setPointMarkers(oMark, tMark);

                    // Contexte pour le re-rendu lors d'un changement d'alternative
                    ctxDep = d; ctxArr = a; ctxStart = startTime;
                    ctxDeadline = arrivalDeadline;
                    ctxCal = (Boolean) result[3];
                    ctxDate = travelDate;
                    currentAlts = alts;
                    currentAltIdx = 0;
                    renderAltBar();

                    if (!alts.isEmpty()) {
                        montrerAlternative(0);
                    } else {
                        afficherResultat(d, a, startTime, j, segs, tripInfo, arrivalDeadline,
                                (Boolean) result[3], travelDate, (Integer) result[7]);
                    }
                } catch (java.util.concurrent.TimeoutException tex) {
                    resultLabel.setText("<html><span style='color:#EF4444'>Timeout : calcul trop long (base DB surchargée ?)</span></html>");
                } catch (Exception ex) {
                    Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                    String msg = cause.getMessage() != null ? cause.getMessage() : cause.getClass().getSimpleName();
                    System.err.println("[RAPTOR ERROR] " + msg);
                    resultLabel.setText("<html><span style='color:#EF4444'>Erreur : " + esc(msg) + "</span></html>");
                } finally {
                    searchBtn.setEnabled(true);
                    searchBtn.setText(SEARCH_LABEL);
                    searchBtn.setBackground(BLUE);
                    searchBtn.repaint();
                }
            }
        };
        worker.execute();
    }

    // Convertit le texte d'un champ en coordonnées [lat, lon] :
    // 1) point choisi sur la carte, 2) suggestion d'adresse retenue, 3) station connue,
    // 4) adresse libre géocodée (Nominatim).
    private double[] resolveEndpoint(String text, double[] custom, String role) throws Exception {
        if (custom != null && text.startsWith(POINT_CARTE)) {
            return custom;
        }
        double[] sug = suggestionCoords.get(text);
        if (sug != null) {
            return sug;
        }
        MetroLoader.StationInfo s = stationMap.get(text);
        if (s != null) {
            return new double[]{s.lat, s.lon};
        }
        double[] cached = geocodeCache.get(text);
        if (cached != null) {
            return cached;
        }
        double[] geo = geocodeAddress(text);
        if (geo == null) {
            throw new Exception("Adresse introuvable (" + role + ") : " + text);
        }
        geocodeCache.put(text, geo);
        return geo;
    }

    // Géocodage d'une adresse via Nominatim (OpenStreetMap), restreint à l'Île-de-France.
    private static double[] geocodeAddress(String query) throws IOException {
        List<String[]> r = geocodeSuggest(query, 1);
        if (r.isEmpty()) return null;
        return new double[]{Double.parseDouble(r.get(0)[0]), Double.parseDouble(r.get(0)[1])};
    }

    // Jusqu'à 'limit' suggestions Nominatim pour un texte : {lat, lon, libellé}.
    private static List<String[]> geocodeSuggest(String query, int limit) throws IOException {
        String url = "https://nominatim.openstreetmap.org/search?format=json&limit=" + limit
                   + "&countrycodes=fr&viewbox=1.35,49.30,3.60,48.05&bounded=1&q="
                   + URLEncoder.encode(query, StandardCharsets.UTF_8);
        HttpURLConnection conn = (HttpURLConnection) URI.create(url).toURL().openConnection();
        conn.setRequestProperty("User-Agent", "MetroEfreiDodo/1.0 Java Swing educational");
        conn.setConnectTimeout(6000);
        conn.setReadTimeout(8000);
        StringBuilder sb = new StringBuilder();
        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) sb.append(line);
        }
        List<String[]> out = new ArrayList<>();
        java.util.regex.Matcher m = java.util.regex.Pattern.compile(
                "\"lat\"\\s*:\\s*\"(-?[0-9.]+)\"\\s*,\\s*\"lon\"\\s*:\\s*\"(-?[0-9.]+)\""
                + "[^{}]*?\"display_name\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"").matcher(sb);
        while (m.find() && out.size() < limit) {
            String name = m.group(3).replace("\\\"", "\"").replace("\\/", "/");
            out.add(new String[]{m.group(1), m.group(2), name});
        }
        return out;
    }

    // ── Autocomplétion ─────────────────────────────────────────────
    // Pendant la frappe : filtre immédiat des stations (insensible aux accents), puis
    // après une pause de 500 ms, jusqu'à 3 suggestions d'adresses Nominatim en fin de liste.
    private void installAutocomplete(JComboBox<String> box) {
        if (!(box.getEditor().getEditorComponent() instanceof JTextField ed)) return;

        javax.swing.Timer debounce = new javax.swing.Timer(500, e -> {
            String q = ed.getText().trim();
            if (q.length() < 4 || q.startsWith(POINT_CARTE)
                    || stationMap.containsKey(q) || suggestionCoords.containsKey(q)) return;
            new SwingWorker<List<String[]>, Void>() {
                @Override protected List<String[]> doInBackground() throws Exception {
                    return geocodeSuggest(q, 3);
                }
                @Override protected void done() {
                    try {
                        List<String[]> res = get();
                        // Le texte a changé entre-temps : suggestions obsolètes.
                        if (!ed.getText().trim().equals(q) || res.isEmpty()) return;
                        List<String> labels = new ArrayList<>();
                        for (String[] r : res) {
                            String label = ADRESSE_PREFIX + shorten(r[2], 70);
                            suggestionCoords.put(label, new double[]{
                                    Double.parseDouble(r[0]), Double.parseDouble(r[1])});
                            labels.add(label);
                        }
                        refreshSuggestions(box, ed, q, labels);
                    } catch (Exception ignored) {}
                }
            }.execute();
        });
        debounce.setRepeats(false);

        ed.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            private void onChange() {
                if (suppressAuto) return;
                SwingUtilities.invokeLater(() -> {
                    if (suppressAuto) return;
                    refreshSuggestions(box, ed, ed.getText().trim(), Collections.emptyList());
                    debounce.restart();
                });
            }
            @Override public void insertUpdate(javax.swing.event.DocumentEvent e)  { onChange(); }
            @Override public void removeUpdate(javax.swing.event.DocumentEvent e)  { onChange(); }
            @Override public void changedUpdate(javax.swing.event.DocumentEvent e) { onChange(); }
        });
    }

    // Reconstruit la liste déroulante : stations filtrées (max 12) + adresses proposées.
    private void refreshSuggestions(JComboBox<String> box, JTextField ed,
            String text, List<String> addressLabels) {
        if (!ed.isFocusOwner()) return;               // champ non actif : ne rien faire
        // Une valeur déjà résolue est sélectionnée : pas de nouvelles suggestions.
        if (stationMap.containsKey(text) || suggestionCoords.containsKey(text)
                || text.startsWith(POINT_CARTE)) {
            box.hidePopup();
            return;
        }
        suppressAuto = true;
        try {
            String norm = normalize(text);
            DefaultComboBoxModel<String> m = new DefaultComboBoxModel<>();
            if (norm.isEmpty()) {
                // Champ vidé : liste complète restaurée.
                m.addElement("— Sélectionner —");
                for (String s : allStationNames) m.addElement(s);
                box.setModel(m);
                ed.setText("");
                box.hidePopup();
                return;
            }
            for (String s : allStationNames) {
                if (normalize(s).contains(norm)) {
                    m.addElement(s);
                    if (m.getSize() >= 12) break;
                }
            }
            for (String a : addressLabels) m.addElement(a);
            box.setModel(m);
            box.setSelectedItem(null);
            ed.setText(text);
            box.hidePopup();
            if (m.getSize() > 0 && box.isShowing()) box.showPopup();
        } finally {
            suppressAuto = false;
        }
    }

    // Minuscules sans accents, pour un filtrage tolérant.
    private static String normalize(String s) {
        String n = java.text.Normalizer.normalize(s.toLowerCase(Locale.FRENCH),
                java.text.Normalizer.Form.NFD);
        return n.replaceAll("\\p{M}", "");
    }

    private static String shorten(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }

    // Informations de ligne pour chaque trip du trajet : trip_id -> {nom, couleur hex}.
    // Une seule requête pour tout le trajet, exécutée hors EDT.
    private Map<String, String[]> loadTripInfo(Journey j) {
        Map<String, String[]> info = new HashMap<>();
        if (j == null || j.destPath == null) return info;
        List<String> tripIds = new ArrayList<>();
        for (Leg leg : j.destPath)
            if (!leg.aPied && leg.tripId != null) tripIds.add(leg.tripId);
        if (tripIds.isEmpty()) return info;
        String placeholders = String.join(",", Collections.nCopies(tripIds.size(), "?"));
        String sql = "SELECT t.trip_id, COALESCE(NULLIF(r.route_short_name,''), r.route_long_name) AS rname, "
                   + "r.route_color FROM trips t JOIN routes r ON r.route_id = t.route_id "
                   + "WHERE t.trip_id IN (" + placeholders + ")";
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int i = 0; i < tripIds.size(); i++) ps.setString(i + 1, tripIds.get(i));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next())
                    info.put(rs.getString("trip_id"),
                             new String[]{rs.getString("rname"), rs.getString("route_color")});
            }
        } catch (Exception e) {
            System.err.println("[TRIP INFO ERROR] " + e.getMessage());
        }
        return info;
    }

    // Construit les segments à dessiner : un tronçon par leg, couleur officielle de la ligne,
    // pointillés pour la marche, y compris la marche initiale et finale vers les points réels.
    private List<MapPanel.RouteSeg> buildRouteSegments(Journey j, double[] originPt, double[] destPt,
            Map<String, String[]> tripInfo) {
        List<MapPanel.RouteSeg> segs = new ArrayList<>();
        if (j == null || j.destPath == null || j.destPath.isEmpty()) return segs;
        Color walkColor = new Color(75, 85, 99);

        // Rendu accroché aux stations parentes : chaque point du tracé utilise les coordonnées
        // de la station parente quand elle existe (clarté visuelle uniquement — l'algorithme
        // conserve l'arrêt réellement choisi, quai ou arrêt enfant).
        String sql = "SELECT st.stop_id, COALESCE(p.stop_lat, s.stop_lat)::float AS lat, "
                   + "COALESCE(p.stop_lon, s.stop_lon)::float AS lon "
                   + "FROM stop_times st JOIN stops s ON s.stop_id = st.stop_id "
                   + "LEFT JOIN stops p ON p.stop_id = s.parent_station "
                   + "WHERE st.trip_id = ? ORDER BY st.stop_sequence";
        try (Connection conn = Database.getConnection()) {
            // stopCoords ne contient que les stations parentes (unique_parent_station) :
            // les coordonnées des arrêts enfants du trajet sont résolues ici en une requête.
            Map<String, double[]> legCoords = resolveLegCoords(conn, j);

            // Marche initiale : point de départ réel -> premier arrêt (si distance non négligeable)
            double[] firstStop = legCoords.get(j.destPath.get(0).fromStop);
            if (originPt != null && firstStop != null
                    && Calculation.haversine(originPt[0], originPt[1], firstStop[0], firstStop[1]) > 40) {
                segs.add(new MapPanel.RouteSeg(List.of(originPt, firstStop), walkColor, true));
            }

            for (Leg leg : j.destPath) {
                double[] from = legCoords.get(leg.fromStop);
                double[] to   = legCoords.get(leg.toStop);
                if (leg.aPied || leg.tripId == null) {
                    if (from != null && to != null && (from[0] != to[0] || from[1] != to[1]))
                        segs.add(new MapPanel.RouteSeg(List.of(from, to), walkColor, true));
                    continue;
                }
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
                int iFrom = ids.indexOf(leg.fromStop);
                int iTo   = ids.indexOf(leg.toStop);
                List<double[]> pts = new ArrayList<>();
                if (iFrom >= 0 && iTo >= 0 && iFrom <= iTo) {
                    for (int i = iFrom; i <= iTo; i++) addIfNew(pts, coords.get(i));
                } else {
                    addIfNew(pts, from);
                    addIfNew(pts, to);
                }
                if (pts.size() >= 2)
                    segs.add(new MapPanel.RouteSeg(pts, routeColor(tripInfo.get(leg.tripId)), false));
            }

            // Marche finale : dernier arrêt -> point d'arrivée réel
            double[] lastStop = legCoords.get(j.destStopId);
            if (destPt != null && lastStop != null
                    && Calculation.haversine(destPt[0], destPt[1], lastStop[0], lastStop[1]) > 40) {
                segs.add(new MapPanel.RouteSeg(List.of(lastStop, destPt), walkColor, true));
            }
        } catch (Exception e) {
            System.err.println("[ROUTE BUILD ERROR] " + e.getMessage());
            // Mode dégradé sans base : lignes droites entre les arrêts connus (stations parentes).
            segs.clear();
            for (Leg leg : j.destPath) {
                double[] from = stopCoords.get(leg.fromStop);
                double[] to   = stopCoords.get(leg.toStop);
                if (from == null || to == null) continue;
                segs.add(new MapPanel.RouteSeg(List.of(from, to),
                        leg.aPied ? walkColor : routeColor(tripInfo.get(leg.tripId)), leg.aPied));
            }
        }
        return segs;
    }

    // Coordonnées de tous les arrêts du trajet : d'abord stopCoords (stations parentes),
    // puis une requête unique pour les identifiants manquants (arrêts enfants), en
    // remontant aux coordonnées de la station parente pour un rendu propre.
    private Map<String, double[]> resolveLegCoords(Connection conn, Journey j) {
        Map<String, double[]> map = new HashMap<>();
        Set<String> wanted = new HashSet<>();
        for (Leg leg : j.destPath) {
            if (leg.fromStop != null) wanted.add(leg.fromStop);
            if (leg.toStop   != null) wanted.add(leg.toStop);
        }
        if (j.destStopId != null) wanted.add(j.destStopId);

        Set<String> missing = new HashSet<>();
        for (String id : wanted) {
            double[] c = stopCoords.get(id);
            if (c != null) map.put(id, c);
            else missing.add(id);
        }
        if (!missing.isEmpty()) {
            String sql = "SELECT s.stop_id, COALESCE(p.stop_lat, s.stop_lat)::float AS lat, "
                       + "COALESCE(p.stop_lon, s.stop_lon)::float AS lon "
                       + "FROM stops s LEFT JOIN stops p ON p.stop_id = s.parent_station "
                       + "WHERE s.stop_id = ANY(?)";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setArray(1, conn.createArrayOf("text", missing.toArray()));
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next())
                        map.put(rs.getString("stop_id"),
                                new double[]{rs.getDouble("lat"), rs.getDouble("lon")});
                }
            } catch (SQLException e) {
                System.err.println("[LEG COORDS ERROR] " + e.getMessage());
            }
        }
        return map;
    }

    // Couleur officielle de la ligne (route_color GTFS), bleu par défaut.
    private static Color routeColor(String[] info) {
        if (info != null && info[1] != null && !info[1].isBlank()) {
            try {
                return new Color(Integer.parseInt(info[1].trim().replace("#", ""), 16));
            } catch (NumberFormatException ignored) {}
        }
        return new Color(37, 99, 235);
    }

    // ── Alternatives multi-objectifs ───────────────────────────────
    // RAPTOR fournit déjà l'heure d'arrivée de TOUS les arrêts atteignables (arrivalTimes)
    // et de quoi reconstruire chaque chemin (legs). On dérive donc plusieurs alternatives
    // en choisissant différents arrêts de destination selon le critère, sans relancer l'algo :
    // plus rapide / moins de changements / moins de marche / accessible fauteuil roulant.
    private List<Alternative> computeAlternatives(Journey base, double[] o, double[] t,
            double rayonArrivee, double maxRayonArrivee, String startTime,
            List<MapPanel.RouteSeg> baseSegs, Map<String, String[]> baseTripInfo) {

        List<Alternative> out = new ArrayList<>();
        // Nécessite les legs du RAPTOR avant (le mode "Arrivée à" ne les expose pas).
        if (base == null || base.destStopId == null || base.legs == null || base.legs.isEmpty()) {
            return out;
        }

        // Un candidat = un arrêt de destination atteint, avec ses métriques.
        class Cand {
            String stopId, arrival;
            List<Leg> path;
            int totalSec, transfers, walkSec, finalWalkSec, initialWalkSec;
            boolean pmr;
        }
        List<Cand> cands = new ArrayList<>();

        try (Connection conn = Database.getConnection()) {
            List<Stops> destStops = Calculation.getNearbyStopsExpanding(
                    conn, t[0], t[1], rayonArrivee, maxRayonArrivee);

            // Chemins vers chaque arrêt candidat + tous les arrêts impliqués.
            Map<String, List<Leg>> paths = new LinkedHashMap<>();
            Set<String> allIds = new HashSet<>();
            for (Stops s : destStops) {
                String sid = s.getStop_id();
                if (base.arrivalTimes.get(sid) == null || paths.containsKey(sid)) continue;
                List<Leg> path = Calculation.reconstructPath(sid, base.legs);
                paths.put(sid, path);
                allIds.add(sid);
                for (Leg leg : path) {
                    if (leg.fromStop != null) allIds.add(leg.fromStop);
                    if (leg.toStop   != null) allIds.add(leg.toStop);
                }
            }
            if (paths.isEmpty()) return out;

            // Coordonnées exactes + accessibilité fauteuil (quai ou station parente) en une requête.
            Map<String, double[]> coords = new HashMap<>();
            Map<String, Boolean>  pmr    = new HashMap<>();
            String sql = "SELECT s.stop_id, s.stop_lat::float AS lat, s.stop_lon::float AS lon, "
                       + "GREATEST(COALESCE(s.wheelchair_boarding,0), COALESCE(p.wheelchair_boarding,0)) AS pmr "
                       + "FROM stops s LEFT JOIN stops p ON p.stop_id = s.parent_station "
                       + "WHERE s.stop_id = ANY(?)";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setArray(1, conn.createArrayOf("text", allIds.toArray()));
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        coords.put(rs.getString("stop_id"),
                                new double[]{rs.getDouble("lat"), rs.getDouble("lon")});
                        pmr.put(rs.getString("stop_id"), rs.getInt("pmr") == 1);
                    }
                }
            }

            for (Map.Entry<String, List<Leg>> e : paths.entrySet()) {
                String sid = e.getKey();
                List<Leg> path = e.getValue();
                double[] sc = coords.get(sid);
                if (sc == null) continue;
                Cand c = new Cand();
                c.stopId  = sid;
                c.arrival = base.arrivalTimes.get(sid);
                c.path    = path;
                c.finalWalkSec = Calculation.walkSeconds(t[0], t[1], sc[0], sc[1]);
                c.totalSec = Calculation.toSeconds(c.arrival) + c.finalWalkSec;
                long transit = path.stream().filter(l -> !l.aPied).count();
                c.transfers = (int) Math.max(0, transit - 1);
                int walk = c.finalWalkSec;
                if (!path.isEmpty()) {
                    double[] fc = coords.get(path.get(0).fromStop);
                    if (fc != null) {
                        c.initialWalkSec = Calculation.walkSeconds(o[0], o[1], fc[0], fc[1]);
                        walk += c.initialWalkSec;
                    }
                    for (Leg l : path) {
                        if (l.aPied) walk += Math.max(0,
                                Calculation.toSeconds(l.arriveTime) - Calculation.toSeconds(l.departTime));
                    }
                } else {
                    walk += Calculation.walkSeconds(o[0], o[1], sc[0], sc[1]);
                }
                c.walkSec = walk;
                // Accessible : tous les arrêts d'embarquement / descente / correspondance sont PMR.
                boolean okPmr = !path.isEmpty() && pmr.getOrDefault(sid, false);
                for (Leg l : path) {
                    if (!pmr.getOrDefault(l.fromStop, false) || !pmr.getOrDefault(l.toStop, false)) {
                        okPmr = false;
                        break;
                    }
                }
                c.pmr = okPmr;
                cands.add(c);
            }
        } catch (Exception e) {
            System.err.println("[ALTERNATIVES ERROR] " + e.getMessage());
            return out;
        }
        if (cands.isEmpty()) return out;

        // Sélection par critère (départage : heure d'arrivée totale).
        Cand fastest = cands.stream()
                .min(Comparator.comparingInt(c -> c.totalSec)).orElse(null);
        Cand fewest = cands.stream()
                .min(Comparator.<Cand>comparingInt(c -> c.transfers)
                        .thenComparingInt(c -> c.totalSec)).orElse(null);
        Cand leastWalk = cands.stream()
                .min(Comparator.<Cand>comparingInt(c -> c.walkSec)
                        .thenComparingInt(c -> c.totalSec)).orElse(null);
        Cand pmrBest = cands.stream().filter(c -> c.pmr)
                .min(Comparator.comparingInt(c -> c.totalSec)).orElse(null);

        // Regroupement : un même chemin retenu par plusieurs critères = un seul bouton.
        Map<Cand, List<String>> picks = new LinkedHashMap<>();
        picks.computeIfAbsent(fastest,   k -> new ArrayList<>()).add("Plus rapide");
        picks.computeIfAbsent(fewest,    k -> new ArrayList<>()).add("Moins de changements");
        picks.computeIfAbsent(leastWalk, k -> new ArrayList<>()).add("Moins de marche");
        if (pmrBest != null) {
            picks.computeIfAbsent(pmrBest, k -> new ArrayList<>()).add("Accessible PMR");
        }

        int startSec = Calculation.toSeconds(startTime);
        for (Map.Entry<Cand, List<String>> e : picks.entrySet()) {
            Cand c = e.getKey();
            if (c == null) continue;
            Journey alt = new Journey(base.arrivalTimes, base.legs, c.stopId, c.arrival,
                    Calculation.fromSeconds(c.totalSec), c.finalWalkSec, c.path);
            List<MapPanel.RouteSeg> segs;
            Map<String, String[]> info;
            if (c.stopId.equals(base.destStopId) && baseSegs != null) {
                segs = baseSegs;
                info = baseTripInfo;
            } else {
                info = loadTripInfo(alt);
                segs = buildRouteSegments(alt, o, t, info);
            }
            String tooltip = "Arrivée " + c.arrival.substring(0, 5)
                    + " · " + c.transfers + " changement" + (c.transfers > 1 ? "s" : "")
                    + " · " + (c.walkSec / 60) + " min de marche"
                    + " · durée " + Math.max(0, (c.totalSec - startSec) / 60) + " min"
                    + (c.pmr ? " · accessible PMR" : "");
            out.add(new Alternative(String.join(" / ", e.getValue()), tooltip, alt, segs, info,
                    c.initialWalkSec));
        }
        return out;
    }

    // Reconstruit la barre de boutons des alternatives (masquée s'il n'y a qu'un résultat).
    private void renderAltBar() {
        altPanel.removeAll();
        if (currentAlts.size() > 1) {
            for (int i = 0; i < currentAlts.size(); i++) {
                final int idx = i;
                JButton b = new JButton(currentAlts.get(i).label);
                styleToggleBtn(b, i == currentAltIdx);
                b.setToolTipText(currentAlts.get(i).tooltip);
                b.addActionListener(e -> montrerAlternative(idx));
                altPanel.add(b);
            }
        }
        int rows = (altPanel.getComponentCount() + 1) / 2;
        altPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, rows == 0 ? 0 : rows * 30 + (rows - 1) * 6));
        altPanel.revalidate();
        altPanel.repaint();
    }

    // Affiche l'alternative demandée et met à jour le style des boutons.
    private void montrerAlternative(int idx) {
        if (idx < 0 || idx >= currentAlts.size()) return;
        currentAltIdx = idx;
        Component[] cs = altPanel.getComponents();
        for (int i = 0; i < cs.length; i++) {
            if (cs[i] instanceof JButton b) styleToggleBtn(b, i == idx);
        }
        Alternative alt = currentAlts.get(idx);
        afficherResultat(ctxDep, ctxArr, ctxStart, alt.journey, alt.segs, alt.tripInfo,
                ctxDeadline, ctxCal, ctxDate, alt.initialWalkSec);
    }

    // Temps de marche du point de départ au premier arrêt d'embarquement du trajet.
    private int computeInitialWalkSec(Journey j, double[] originPt) {
        if (j == null || j.destPath == null || j.destPath.isEmpty() || originPt == null) return 0;
        String sid = j.destPath.get(0).fromStop;
        if (sid == null) return 0;
        String sql = "SELECT stop_lat::float, stop_lon::float FROM stops WHERE stop_id = ?";
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, sid);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Calculation.walkSeconds(originPt[0], originPt[1],
                            rs.getDouble(1), rs.getDouble(2));
                }
            }
        } catch (SQLException e) {
            System.err.println("[INITIAL WALK ERROR] " + e.getMessage());
        }
        return 0;
    }

    private static void addIfNew(List<double[]> list, double[] pt) {
        if (pt == null) return;
        if (!list.isEmpty()) {
            double[] last = list.get(list.size() - 1);
            if (last[0] == pt[0] && last[1] == pt[1]) return;
        }
        list.add(pt);
    }

    private void afficherResultat(String dep, String arr, String startTime, Journey j,
            List<MapPanel.RouteSeg> segs, Map<String, String[]> tripInfo,
            String arrivalDeadline, Boolean calendarState, java.time.LocalDate travelDate,
            int initialWalkSec) {
        mapPanel.clearRoute();
        if (j.destStopId == null) {
            resultLabel.setText("<html><b>" + esc(dep) + " → " + esc(arr) + "</b>"
                    + "<br><span style='color:#EF4444'>Aucun trajet trouvé.</span>"
                    + "<br><small>Essayez un rayon plus large, une autre heure ou une autre date.</small></html>");
            return;
        }
        if (!segs.isEmpty()) mapPanel.setRouteSegments(segs);

        // ── Calcul durée et correspondances ───────────────────────
        // Utiliser l'heure du premier leg (départ réel) plutôt que l'heure de recherche
        String actualDep = (!j.destPath.isEmpty() && j.destPath.get(0).departTime != null)
                ? j.destPath.get(0).departTime : startTime;
        // Durée porte à porte : la marche initiale précède le départ du premier véhicule.
        int startSec    = Calculation.toSeconds(actualDep) - Math.max(0, initialWalkSec);
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
        // État du filtre calendrier pour la date choisie
        if (calendarState != null) {
            String dateStr = travelDate.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"));
            if (calendarState) {
                sb.append("<span style='color:#10B981;font-size:10px'>Horaires du ")
                  .append(dateStr).append("</span><br>");
            } else {
                sb.append("<span style='color:#F59E0B;font-size:10px'>Aucun service le ")
                  .append(dateStr).append(" dans les données GTFS — horaires non filtrés</span><br>");
            }
        }
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

        // Marche initiale : point de départ -> premier arrêt d'embarquement
        if (initialWalkSec > 60) {
            sb.append("<span style='color:#9CA3AF'>&#x1F6B6; ").append(initialWalkSec / 60)
              .append(" min à pied jusqu'au premier arrêt</span><br>");
        }

        for (Leg leg : j.destPath) {
            if (leg.aPied) {
                sb.append("<span style='color:#9CA3AF'>&#x1F6B6; Correspondance à pied &nbsp;")
                  .append(leg.departTime, 0, 5).append(" → ").append(leg.arriveTime, 0, 5)
                  .append("</span><br>");
            } else {
                // Nom et couleur officielle de la ligne (route_color GTFS)
                String[] info = tripInfo.get(leg.tripId);
                String rname = (info != null && info[0] != null) ? info[0] : "Ligne";
                String hex   = (info != null && info[1] != null && !info[1].isBlank())
                        ? info[1].trim().replace("#", "") : "2563EB";
                sb.append("<span style='color:#").append(hex).append("'>&#x1F687; <b>Ligne ")
                  .append(esc(rname)).append("</b></span> <span style='color:#374151'>")
                  .append(leg.departTime, 0, 5).append(" → ").append(leg.arriveTime, 0, 5)
                  .append("</span><br>");
            }
        }
        if (j.finalWalkSeconds > 60) {
            sb.append("<span style='color:#9CA3AF'>&#x1F6B6; +")
              .append(j.finalWalkSeconds / 60).append(" min à pied</span>");
        }

        // ── CO2 ───────────────────────────────────────────────────────
        double transitKm = 0, walkKm = 0;
        for (Leg leg : j.destPath) {
            int legSec = Calculation.toSeconds(leg.arriveTime) - Calculation.toSeconds(leg.departTime);
            if (legSec < 0) legSec = 0;
            if (leg.aPied) walkKm   += legSec / 3600.0 * 5.0;
            else            transitKm += legSec / 3600.0 * 30.0;
        }
        walkKm += j.finalWalkSeconds / 3600.0 * 5.0;
        double totalKm    = transitKm + walkKm;
        double co2Metro   = transitKm * 4;
        double co2Voiture = totalKm * 180;
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
        if (calendarState != null) {
            String dateStr = travelDate.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"));
            det.append(calendarState
                ? "<p><span style='color:#10B981'>Horaires filtrés pour le " + dateStr + "</span></p>"
                : "<p><span style='color:#F59E0B'>Aucun service actif le " + dateStr
                  + " dans les données GTFS — horaires non filtrés</span></p>");
        }
        det.append("<hr>");

        if (initialWalkSec > 60 && !j.destPath.isEmpty()) {
            String firstName = stopIdToName.getOrDefault(j.destPath.get(0).fromStop,
                    j.destPath.get(0).fromStop);
            det.append("<p>&#x1F6B6; <b>Marche initiale</b> (~").append(initialWalkSec / 60)
               .append(" min)<br><span style='color:#6B7280'>Du point de départ à ")
               .append(esc(firstName)).append("</span></p>");
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
                String[] info = tripInfo.get(leg.tripId);
                String routeName = (info != null && info[0] != null) ? "Ligne " + info[0] : "Ligne inconnue";
                String hex = (info != null && info[1] != null && !info[1].isBlank())
                        ? info[1].trim().replace("#", "") : "2563EB";
                det.append("<p><b style='color:#").append(hex).append("'>").append(esc(routeName))
                   .append("</b> &nbsp;&#x1F687;<br>")
                   .append("Départ : <b>").append(leg.departTime, 0, 5).append("</b> — ")
                   .append(esc(fromName)).append("<br>")
                   .append("Arrivée : <b>").append(leg.arriveTime, 0, 5).append("</b> — ")
                   .append(esc(toName)).append("<br>")
                   .append("<span style='color:#6B7280'>Durée : ").append(legMin).append(" min</span></p>");
            }
        }
        if (j.finalWalkSeconds > 60)
            det.append("<p>&#x1F6B6; Marche finale : <b>").append(j.finalWalkSeconds / 60).append(" min</b></p>");
        det.append("</body></html>");

        setResultat(sb.toString(), det.toString());

        // Cadrer la carte sur l'itinéraire complet
        List<double[]> allPts = new ArrayList<>();
        for (MapPanel.RouteSeg s : segs) allPts.addAll(s.points);
        if (!allPts.isEmpty()) mapPanel.fitBounds(allPts);
        else mapPanel.focusStation(dep);
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
        SwingWorker<Object[], Void> w = new SwingWorker<>() {
            @Override protected Object[] doInBackground() throws Exception { return calculerKruskal(); }
            @Override protected void done() {
                try {
                    Object[] r = get(120, java.util.concurrent.TimeUnit.SECONDS);
                    setResultat((String) r[0], (String) r[1]);
                    // Dessin de l'arborescence sur la carte, couleurs officielles des lignes
                    @SuppressWarnings("unchecked")
                    List<MapPanel.RouteSeg> segs = (List<MapPanel.RouteSeg>) r[2];
                    if (segs != null && !segs.isEmpty()) {
                        mapPanel.setRouteSegments(segs);
                        List<double[]> pts = new ArrayList<>();
                        for (MapPanel.RouteSeg s : segs) pts.addAll(s.points);
                        mapPanel.fitBounds(pts);
                    }
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

    // Renvoie {résumé HTML, détail HTML, segments de l'ACM à dessiner sur la carte}.
    private Object[] calculerKruskal() throws Exception {
        // Paires d'arrêts consécutifs dans les trips (LEAD évite la self-join lourde),
        // accrochées aux stations parentes et enrichies de la couleur officielle de la ligne
        // (route_color) pour un rendu type plan RATP.
        String sql =
            "SELECT r.route_id AS rid, "
            + "COALESCE(p1.stop_id, s1.stop_id) AS fid, "
            + "COALESCE(p1.stop_lat, s1.stop_lat)::float AS flat, "
            + "COALESCE(p1.stop_lon, s1.stop_lon)::float AS flon, "
            + "COALESCE(p2.stop_id, s2.stop_id) AS tid, "
            + "COALESCE(p2.stop_lat, s2.stop_lat)::float AS tlat, "
            + "COALESCE(p2.stop_lon, s2.stop_lon)::float AS tlon, "
            + "MIN(r.route_color) AS color "
            + "FROM (SELECT trip_id, stop_id, "
            + "      LEAD(stop_id) OVER (PARTITION BY trip_id ORDER BY stop_sequence) AS nxt "
            + "      FROM stop_times) t "
            + "JOIN trips tr ON tr.trip_id = t.trip_id "
            + "JOIN routes r ON r.route_id = tr.route_id "
            + "JOIN stops s1 ON s1.stop_id = t.stop_id "
            + "JOIN stops s2 ON s2.stop_id = t.nxt "
            + "LEFT JOIN stops p1 ON p1.stop_id = s1.parent_station "
            + "LEFT JOIN stops p2 ON p2.stop_id = s2.parent_station "
            + "WHERE t.nxt IS NOT NULL AND s1.stop_lat IS NOT NULL AND s2.stop_lat IS NOT NULL "
            + "GROUP BY 1, 2, 3, 4, 5, 6, 7";

        Map<String, Integer> idx = new LinkedHashMap<>();
        Map<String, List<KEdge>> routeEdges = new LinkedHashMap<>();

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
                routeEdges.computeIfAbsent(rs.getString("rid"), k -> new ArrayList<>())
                        .add(new KEdge(fi, ti,
                                Calculation.haversine(flat, flon, tlat, tlon),
                                new double[]{flat, flon, tlat, tlon},
                                rs.getString("color")));
            }
        }

        int n = idx.size();
        if (n == 0) return new Object[]{"<html>Aucune donnée disponible.</html>", null, null};

        // Filtrage des sauts express (RER notamment) : au sein d'une même ligne, une arête
        // que l'on peut remplacer par un chemin d'autres arêtes de longueur comparable
        // (<= 2x) est un trajet direct qui saute des gares : on ne la trace pas.
        List<KEdge> kept = new ArrayList<>();
        for (List<KEdge> edges : routeEdges.values()) {
            Map<Integer, List<KEdge>> adj = new HashMap<>();
            for (KEdge e : edges) {
                adj.computeIfAbsent(e.fi, k -> new ArrayList<>()).add(e);
                adj.computeIfAbsent(e.ti, k -> new ArrayList<>()).add(e);
            }
            List<KEdge> sorted = new ArrayList<>(edges);
            sorted.sort((x, y) -> Double.compare(y.w, x.w));      // plus longues d'abord
            for (KEdge e : sorted) {
                if (kAltPath(adj, e, 2.0 * e.w) <= 2.0 * e.w) e.removed = true;
            }
            for (KEdge e : edges) {
                if (!e.removed) kept.add(e);
            }
        }

        // Arêtes dédupliquées entre lignes pour les statistiques (Kruskal inchangé).
        Map<Long, KEdge> uniq = new LinkedHashMap<>();
        for (KEdge e : kept) {
            long key = (long) Math.min(e.fi, e.ti) * 1_000_000L + Math.max(e.fi, e.ti);
            uniq.putIfAbsent(key, e);
        }
        List<int[]>  edgeIdx = new ArrayList<>();
        List<Double> edgeW   = new ArrayList<>();
        for (KEdge e : uniq.values()) {
            edgeIdx.add(new int[]{e.fi, e.ti});
            edgeW.add(e.w);
        }

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

        // Rendu carte : réseau complet filtré (toutes les arêtes station à station hors
        // sauts express, couleurs de ligne), sans les trous du seul arbre couvrant.
        List<MapPanel.RouteSeg> segs = new ArrayList<>();
        for (KEdge e : kept) {
            double[] p = e.pts;
            segs.add(new MapPanel.RouteSeg(
                    List.of(new double[]{p[0], p[1]}, new double[]{p[2], p[3]}),
                    routeColor(new String[]{null, e.color}), false));
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

        return new Object[]{summary, det.toString(), segs};
    }

    // Arête d'une ligne (indices de stations dans idx), pour le filtrage des sauts express.
    private static class KEdge {
        final int fi, ti;
        final double w;
        final double[] pts;    // {flat, flon, tlat, tlon}
        final String color;
        boolean removed = false;

        KEdge(int fi, int ti, double w, double[] pts, String color) {
            this.fi = fi;
            this.ti = ti;
            this.w = w;
            this.pts = pts;
            this.color = color;
        }
    }

    // Plus court chemin entre les extrémités de excl dans le graphe de SA ligne, sans excl
    // ni les arêtes déjà retirées. Abandonne dès que la distance dépasse limit.
    private static double kAltPath(Map<Integer, List<KEdge>> adj, KEdge excl, double limit) {
        Map<Integer, Double> dist = new HashMap<>();
        PriorityQueue<double[]> pq = new PriorityQueue<>(Comparator.comparingDouble(a -> a[1]));
        dist.put(excl.fi, 0.0);
        pq.add(new double[]{excl.fi, 0.0});
        while (!pq.isEmpty()) {
            double[] cur = pq.poll();
            int u = (int) cur[0];
            double du = cur[1];
            if (du > dist.getOrDefault(u, Double.MAX_VALUE)) continue;
            if (u == excl.ti) return du;
            if (du > limit) return Double.MAX_VALUE;
            for (KEdge e : adj.getOrDefault(u, Collections.emptyList())) {
                if (e == excl || e.removed) continue;
                int v = (e.fi == u) ? e.ti : e.fi;
                double nd = du + e.w;
                if (nd < dist.getOrDefault(v, Double.MAX_VALUE)) {
                    dist.put(v, nd);
                    pq.add(new double[]{v, nd});
                }
            }
        }
        return Double.MAX_VALUE;
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
            @Override protected String[] doInBackground() throws Exception { return calculerBFS2(); }
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

    private String[] calculerBFS2() throws Exception {
        // 1. Connexion et chargement des données (exécuté en tâche de fond)
        // Adaptation minime : Database.getConnection() lit DATABASE_URL depuis
        // l'environnement OU db.properties (SupabaseConnector exige la variable d'env).
        Connection connection = Database.getConnection();
        DataLoader loader = new DataLoader(connection);
        Graph graph = GraphBuilder.build(loader);

        // 2. Appel de ta logique BFS
        boolean connected = BFS.isConnected(graph);

        // 3. Préparation des résultats pour l'affichage (index 0 = titre/statut, index 1 = détails)
        String statut = connected
                ? "<html><b style='color:#10B981'>Le graphe est connexe (Connected)</b></html>"
                : "<html><b style='color:#EF4444'>Le graphe n'est pas connexe (Not connected)</b></html>";

        String details = "Nombre total de sommets : " + graph.getVertices().size();

        // On retourne le tableau attendu par le SwingWorker [r[0], r[1]]
        return new String[]{statut, details};
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
