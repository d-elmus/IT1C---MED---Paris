import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.net.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.*;

public class MapPanel extends JPanel {

    private double centerLat = 48.8566;
    private double centerLon = 2.3522;
    // Zoom fractionnaire : les tuiles sont rendues au niveau entier le plus proche
    // puis mises a l'echelle, ce qui permet des demi-crans de zoom.
    private double zoom = 12.0;

    // Le projet couvre uniquement l'Ile-de-France : zoom et centre bornes en consequence.
    private static final double MIN_ZOOM  = 10.0;
    private static final double MAX_ZOOM  = 17.0;
    private static final double ZOOM_STEP = 0.5;
    private static final double IDF_LAT_MIN = 48.05, IDF_LAT_MAX = 49.30;
    private static final double IDF_LON_MIN = 1.35,  IDF_LON_MAX = 3.60;

    // Couleur des stations accessibles PMR (bleu)
    private static final Color PMR_BLUE = new Color(0, 85, 200);

    /** Segment d'itineraire a dessiner : suite de points, couleur de ligne, marche a pied ou non. */
    public static class RouteSeg {
        public final List<double[]> points;   // [lat, lon]
        public final Color color;
        public final boolean walk;            // true = marche a pied (pointilles)

        public RouteSeg(List<double[]> points, Color color, boolean walk) {
            this.points = points;
            this.color  = color;
            this.walk   = walk;
        }
    }

    private List<MetroLoader.StationInfo> stations = new ArrayList<>();
    private List<double[]> routePoints = new ArrayList<>();
    private List<RouteSeg> routeSegs   = new ArrayList<>();
    private final Map<String, BufferedImage> tileCache = new ConcurrentHashMap<>();
    private final Set<String> pendingTiles = ConcurrentHashMap.newKeySet();
    private final ExecutorService tileLoader = Executors.newFixedThreadPool(4);

    private Point dragStartScreen;
    private double dragStartLat, dragStartLon;
    private MetroLoader.StationInfo hoveredStation;

    // Callback invoked when user clicks a station marker
    private java.util.function.Consumer<MetroLoader.StationInfo> onStationClick;

    // Mode "choisir un point sur la carte" : le prochain clic renvoie [lat, lon].
    private java.util.function.Consumer<double[]> pointPickCallback;

    // Marqueurs personnalises (adresse ou point choisi sur la carte)
    private double[] markerDepart;   // [lat, lon] ou null
    private double[] markerArrivee;  // [lat, lon] ou null

    public MapPanel(List<MetroLoader.StationInfo> stations) {
        this.stations = new ArrayList<>(stations);
        setBackground(new Color(210, 210, 210));

        MouseAdapter ma = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                dragStartScreen = e.getPoint();
                dragStartLat = centerLat;
                dragStartLon = centerLon;
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                // If mouse barely moved, treat as click
                if (dragStartScreen != null) {
                    double dist = e.getPoint().distance(dragStartScreen);
                    if (dist < 5) {
                        if (pointPickCallback != null) {
                            java.util.function.Consumer<double[]> cb = pointPickCallback;
                            pointPickCallback = null;
                            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                            cb.accept(screenToLatLon(e.getX(), e.getY()));
                            return;
                        }
                        if (hoveredStation != null && onStationClick != null) {
                            onStationClick.accept(hoveredStation);
                        }
                    }
                }
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                if (dragStartScreen == null) return;
                double dx = e.getX() - dragStartScreen.x;
                double dy = e.getY() - dragStartScreen.y;
                double ts = tileSize();
                int tz = tileZ();
                double newTX = lonToTileX(dragStartLon, tz) - dx / ts;
                double newTY = latToTileY(dragStartLat, tz) - dy / ts;
                centerLon = tileXToLon(newTX, tz);
                centerLat = tileYToLat(newTY, tz);
                clampCenter();
                repaint();
            }

            @Override
            public void mouseMoved(MouseEvent e) {
                MetroLoader.StationInfo nearest = null;
                double minDist = 12;
                int w = getWidth(), h = getHeight();
                for (MetroLoader.StationInfo s : stations) {
                    int[] p = stationToPixel(s, w, h);
                    double d = Math.hypot(e.getX() - p[0], e.getY() - p[1]);
                    if (d < minDist) { minDist = d; nearest = s; }
                }
                if (!Objects.equals(nearest, hoveredStation)) {
                    hoveredStation = nearest;
                    if (pointPickCallback == null) {
                        setCursor(nearest != null
                                ? Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR)
                                : Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                    }
                    repaint();
                }
            }
        };
        addMouseListener(ma);
        addMouseMotionListener(ma);
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        // Mouse wheel zoom : demi-crans, borne a l'Ile-de-France
        addMouseWheelListener(e -> {
            int mx = (int) e.getX(), my = (int) e.getY();
            int w = getWidth(), h = getHeight();

            double newZoom = zoom - Math.signum(e.getPreciseWheelRotation()) * ZOOM_STEP;
            newZoom = Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, newZoom));
            if (newZoom == zoom) return;

            // Le point geographique sous le curseur reste fixe pendant le zoom.
            double[] geo = screenToLatLon(mx, my);
            zoom = newZoom;
            int tz = tileZ();
            double ts = tileSize();
            double gTX = lonToTileX(geo[1], tz);
            double gTY = latToTileY(geo[0], tz);
            centerLon = tileXToLon(gTX - (mx - w / 2.0) / ts, tz);
            centerLat = tileYToLat(gTY - (my - h / 2.0) / ts, tz);
            clampCenter();
            pendingTiles.clear();
            repaint();
        });
    }

    // ── Public API ───────────────────────────────────────────────

    public void setStations(List<MetroLoader.StationInfo> s) {
        this.stations = new ArrayList<>(s);
        repaint();
    }

    public void setOnStationClick(java.util.function.Consumer<MetroLoader.StationInfo> cb) {
        this.onStationClick = cb;
    }

    /** Active le mode "choisir un point" : le prochain clic sur la carte renvoie [lat, lon]. */
    public void startPointPick(java.util.function.Consumer<double[]> cb) {
        this.pointPickCallback = cb;
        setCursor(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR));
    }

    public void cancelPointPick() {
        this.pointPickCallback = null;
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    }

    /** Positionne les marqueurs depart / arrivee personnalises (null pour effacer). */
    public void setPointMarkers(double[] depart, double[] arrivee) {
        this.markerDepart  = depart;
        this.markerArrivee = arrivee;
        repaint();
    }

    /** Center the map on a named station. */
    public void focusStation(String name) {
        for (MetroLoader.StationInfo s : stations) {
            if (s.name.equals(name)) {
                centerLat = s.lat;
                centerLon = s.lon;
                if (zoom < 14) zoom = 14;
                clampCenter();
                repaint();
                return;
            }
        }
    }

    /** Cadre la carte sur une liste de points [lat, lon] (itineraire complet). */
    public void fitBounds(List<double[]> pts) {
        if (pts == null || pts.isEmpty()) return;
        double latMin = Double.MAX_VALUE, latMax = -Double.MAX_VALUE;
        double lonMin = Double.MAX_VALUE, lonMax = -Double.MAX_VALUE;
        for (double[] p : pts) {
            latMin = Math.min(latMin, p[0]); latMax = Math.max(latMax, p[0]);
            lonMin = Math.min(lonMin, p[1]); lonMax = Math.max(lonMax, p[1]);
        }
        centerLat = (latMin + latMax) / 2;
        centerLon = (lonMin + lonMax) / 2;
        int w = Math.max(getWidth(), 200), h = Math.max(getHeight(), 200);
        // Plus grand zoom (par demi-crans) qui contient la boite avec une marge de 15 %.
        double best = MIN_ZOOM;
        for (double z = MIN_ZOOM; z <= MAX_ZOOM; z += ZOOM_STEP) {
            double pxW = Math.abs(lonToTileX(lonMax, z) - lonToTileX(lonMin, z)) * 256;
            double pxH = Math.abs(latToTileY(latMin, z) - latToTileY(latMax, z)) * 256;
            if (pxW <= w * 0.85 && pxH <= h * 0.85) best = z; else break;
        }
        zoom = best;
        clampCenter();
        pendingTiles.clear();
        repaint();
    }

    public void setRoute(List<double[]> points) {
        this.routePoints = new ArrayList<>(points);
        this.routeSegs   = new ArrayList<>();
        repaint();
    }

    /** Itineraire par segments : couleur de ligne par troncon, pointilles pour la marche. */
    public void setRouteSegments(List<RouteSeg> segs) {
        this.routeSegs   = new ArrayList<>(segs);
        this.routePoints = new ArrayList<>();
        repaint();
    }

    public void clearRoute() {
        this.routePoints = new ArrayList<>();
        this.routeSegs   = new ArrayList<>();
        repaint();
    }

    public void shutdown() {
        tileLoader.shutdownNow();
    }

    // ── Painting ─────────────────────────────────────────────────

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,  RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);

        int w = getWidth(), h = getHeight();
        int tz = tileZ();
        double ts = tileSize();
        double cTX = lonToTileX(centerLon, tz);
        double cTY = latToTileY(centerLat, tz);
        int maxTile = (1 << tz) - 1;

        int x0 = (int) Math.floor(cTX - w / (2 * ts)) - 1;
        int x1 = (int) Math.floor(cTX + w / (2 * ts)) + 1;
        int y0 = (int) Math.floor(cTY - h / (2 * ts)) - 1;
        int y1 = (int) Math.floor(cTY + h / (2 * ts)) + 1;

        int drawSize = (int) Math.ceil(ts) + 1;   // +1 evite les jointures visibles entre tuiles
        for (int ty = y0; ty <= y1; ty++) {
            for (int tx = x0; tx <= x1; tx++) {
                if (tx < 0 || ty < 0 || tx > maxTile || ty > maxTile) continue;
                int px = (int) Math.round((tx - cTX) * ts + w / 2.0);
                int py = (int) Math.round((ty - cTY) * ts + h / 2.0);
                String key = tz + "/" + tx + "/" + ty;
                BufferedImage tile = tileCache.get(key);
                if (tile != null) {
                    g2.drawImage(tile, px, py, drawSize, drawSize, null);
                } else {
                    g2.setColor(new Color(220, 220, 220));
                    g2.fillRect(px, py, drawSize, drawSize);
                    g2.setColor(new Color(190, 190, 190));
                    g2.drawRect(px, py, drawSize, drawSize);
                    requestTile(tz, tx, ty);
                }
            }
        }

        // Route overlay
        if (!routeSegs.isEmpty()) {
            paintRouteSegments(g2, w, h);
        } else if (routePoints.size() >= 2) {
            g2.setColor(new Color(59, 130, 246, 60));
            g2.setStroke(new BasicStroke(9f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            for (int i = 0; i < routePoints.size() - 1; i++) {
                int[] p1 = latLonToPixel(routePoints.get(i)[0],   routePoints.get(i)[1],   w, h);
                int[] p2 = latLonToPixel(routePoints.get(i+1)[0], routePoints.get(i+1)[1], w, h);
                g2.drawLine(p1[0], p1[1], p2[0], p2[1]);
            }
            g2.setColor(new Color(37, 99, 235));
            g2.setStroke(new BasicStroke(4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            for (int i = 0; i < routePoints.size() - 1; i++) {
                int[] p1 = latLonToPixel(routePoints.get(i)[0],   routePoints.get(i)[1],   w, h);
                int[] p2 = latLonToPixel(routePoints.get(i+1)[0], routePoints.get(i+1)[1], w, h);
                g2.drawLine(p1[0], p1[1], p2[0], p2[1]);
            }
            g2.setStroke(new BasicStroke(1f));
            int[] pS = latLonToPixel(routePoints.get(0)[0], routePoints.get(0)[1], w, h);
            int[] pE = latLonToPixel(routePoints.get(routePoints.size()-1)[0], routePoints.get(routePoints.size()-1)[1], w, h);
            paintEndpoint(g2, pS, new Color(34, 197, 94));
            paintEndpoint(g2, pE, new Color(239, 68, 68));
        }

        // Station markers : rouge par defaut, bleu pour les stations accessibles PMR
        for (MetroLoader.StationInfo s : stations) {
            int[] p = stationToPixel(s, w, h);
            int px = p[0], py = p[1];
            if (px < -15 || px > w + 15 || py < -15 || py > h + 15) continue;
            g2.setColor(Color.WHITE);
            g2.fillOval(px - 6, py - 6, 13, 13);
            g2.setColor(s.wheelchair ? PMR_BLUE : new Color(227, 5, 28));
            g2.fillOval(px - 5, py - 5, 11, 11);
        }

        // Marqueurs personnalises (adresse / point carte)
        if (markerDepart != null)  paintCustomMarker(g2, markerDepart,  new Color(34, 197, 94),  "Départ",  w, h);
        if (markerArrivee != null) paintCustomMarker(g2, markerArrivee, new Color(239, 68, 68),  "Arrivée", w, h);

        // Hovered station highlight + tooltip
        if (hoveredStation != null) {
            int[] p = stationToPixel(hoveredStation, w, h);
            int px = p[0], py = p[1];

            // Glow ring
            g2.setColor(new Color(227, 5, 28, 70));
            g2.fillOval(px - 11, py - 11, 23, 23);
            g2.setColor(new Color(227, 5, 28));
            g2.setStroke(new BasicStroke(2f));
            g2.drawOval(px - 11, py - 11, 23, 23);
            g2.setStroke(new BasicStroke(1f));

            // Tooltip
            String name = hoveredStation.name;
            g2.setFont(new Font("Segoe UI", Font.BOLD, 13));
            FontMetrics fm = g2.getFontMetrics();
            int tw = fm.stringWidth(name) + 18;
            int th = 28;
            int bx = px + 15, by = py - 14;
            if (bx + tw > w - 4) bx = px - tw - 10;
            if (by < 4) by = py + 15;
            if (by + th > h - 4) by = py - th - 4;

            g2.setColor(new Color(20, 20, 20, 220));
            g2.fillRoundRect(bx, by, tw, th, 7, 7);
            g2.setColor(new Color(227, 5, 28));
            g2.fillRoundRect(bx, by, 4, th, 3, 3);
            g2.setColor(Color.WHITE);
            g2.drawString(name, bx + 10, by + 18);
        }

        // Bandeau d'aide en mode selection de point
        if (pointPickCallback != null) {
            g2.setFont(new Font("Segoe UI", Font.BOLD, 12));
            FontMetrics pfm = g2.getFontMetrics();
            String hint = "Cliquez sur la carte pour choisir le point";
            int hw = pfm.stringWidth(hint) + 24;
            g2.setColor(new Color(20, 20, 20, 210));
            g2.fillRoundRect((w - hw) / 2, 12, hw, 28, 8, 8);
            g2.setColor(Color.WHITE);
            g2.drawString(hint, (w - hw) / 2 + 12, 31);
        }

        // Attribution bottom-right
        g2.setFont(new Font("SansSerif", Font.PLAIN, 11));
        FontMetrics fm = g2.getFontMetrics();
        String attr = "© OpenStreetMap contributors";
        int aw = fm.stringWidth(attr) + 12;
        g2.setColor(new Color(255, 255, 255, 200));
        g2.fillRect(w - aw, h - 18, aw, 18);
        g2.setColor(new Color(50, 50, 50));
        g2.drawString(attr, w - aw + 6, h - 4);

        // Zoom level indicator
        g2.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        g2.setColor(new Color(255, 255, 255, 200));
        String zoomStr = (zoom == Math.floor(zoom))
                ? "Zoom " + (int) zoom : String.format("Zoom %.1f", zoom);
        g2.fillRoundRect(8, h - 24, fm.stringWidth(zoomStr) + 12, 18, 4, 4);
        g2.setColor(new Color(50, 50, 50));
        g2.drawString(zoomStr, 14, h - 9);

        paintLegend(g2, w, h);

        g2.dispose();
    }

    // Légende en bas à droite, au-dessus de l'attribution OpenStreetMap.
    private void paintLegend(Graphics2D g2, int w, int h) {
        int lw = 176, lh = 78;
        int lx = w - lw - 8, ly = h - 18 - 6 - lh;
        g2.setColor(new Color(255, 255, 255, 225));
        g2.fillRoundRect(lx, ly, lw, lh, 8, 8);
        g2.setColor(new Color(210, 210, 210));
        g2.drawRoundRect(lx, ly, lw, lh, 8, 8);

        g2.setFont(new Font("Segoe UI", Font.PLAIN, 10));
        int tx = lx + 28, ty = ly + 16, step = 17;
        g2.setColor(new Color(60, 60, 60));

        // Station classique (rond rouge)
        g2.setColor(Color.WHITE);              g2.fillOval(lx + 10, ty - 8, 11, 11);
        g2.setColor(new Color(227, 5, 28));    g2.fillOval(lx + 11, ty - 7, 9, 9);
        g2.setColor(new Color(60, 60, 60));    g2.drawString("Station", tx, ty);
        ty += step;

        // Station accessible PMR (rond bleu)
        g2.setColor(Color.WHITE);              g2.fillOval(lx + 10, ty - 8, 11, 11);
        g2.setColor(PMR_BLUE);                 g2.fillOval(lx + 11, ty - 7, 9, 9);
        g2.setColor(new Color(60, 60, 60));    g2.drawString("Station accessible PMR", tx, ty);
        ty += step;

        // Trajet en véhicule (trait plein, couleur de ligne)
        g2.setColor(new Color(37, 99, 235));
        g2.setStroke(new BasicStroke(3.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.drawLine(lx + 8, ty - 4, lx + 24, ty - 4);
        g2.setColor(new Color(60, 60, 60));
        g2.drawString("Trajet (couleur de ligne)", tx, ty);
        ty += step;

        // Marche à pied (pointillés gris)
        g2.setColor(new Color(75, 85, 99));
        g2.setStroke(new BasicStroke(2.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND,
                10f, new float[]{4f, 5f}, 0f));
        g2.drawLine(lx + 8, ty - 4, lx + 24, ty - 4);
        g2.setColor(new Color(60, 60, 60));
        g2.drawString("Marche à pied", tx, ty);
        g2.setStroke(new BasicStroke(1f));
    }

    // Trace les segments : contour blanc + couleur de ligne, pointilles pour la marche.
    private void paintRouteSegments(Graphics2D g2, int w, int h) {
        // 1. Contour blanc sous les troncons en vehicule (lisibilite sur la carte)
        g2.setColor(Color.WHITE);
        g2.setStroke(new BasicStroke(8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        for (RouteSeg seg : routeSegs) {
            if (seg.walk) continue;
            drawPolyline(g2, seg.points, w, h);
        }
        // 2. Troncons en vehicule, couleur officielle de la ligne
        for (RouteSeg seg : routeSegs) {
            if (seg.walk) continue;
            g2.setColor(seg.color != null ? seg.color : new Color(37, 99, 235));
            g2.setStroke(new BasicStroke(4.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            drawPolyline(g2, seg.points, w, h);
        }
        // 3. Troncons a pied : pointilles gris fonce
        Stroke dashed = new BasicStroke(3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND,
                10f, new float[]{6f, 8f}, 0f);
        g2.setStroke(dashed);
        for (RouteSeg seg : routeSegs) {
            if (!seg.walk) continue;
            g2.setColor(seg.color != null ? seg.color : new Color(75, 85, 99));
            drawPolyline(g2, seg.points, w, h);
        }
        g2.setStroke(new BasicStroke(1f));

        // 4. Points de depart / arrivee de l'itineraire
        List<double[]> first = routeSegs.get(0).points;
        List<double[]> last  = routeSegs.get(routeSegs.size() - 1).points;
        if (!first.isEmpty() && !last.isEmpty()) {
            int[] pS = latLonToPixel(first.get(0)[0], first.get(0)[1], w, h);
            int[] pE = latLonToPixel(last.get(last.size()-1)[0], last.get(last.size()-1)[1], w, h);
            paintEndpoint(g2, pS, new Color(34, 197, 94));
            paintEndpoint(g2, pE, new Color(239, 68, 68));
        }
    }

    private void drawPolyline(Graphics2D g2, List<double[]> pts, int w, int h) {
        for (int i = 0; i < pts.size() - 1; i++) {
            int[] p1 = latLonToPixel(pts.get(i)[0],   pts.get(i)[1],   w, h);
            int[] p2 = latLonToPixel(pts.get(i+1)[0], pts.get(i+1)[1], w, h);
            g2.drawLine(p1[0], p1[1], p2[0], p2[1]);
        }
    }

    private void paintEndpoint(Graphics2D g2, int[] p, Color c) {
        g2.setColor(Color.WHITE);
        g2.fillOval(p[0]-8, p[1]-8, 17, 17);
        g2.setColor(c);
        g2.fillOval(p[0]-7, p[1]-7, 15, 15);
    }

    // Marqueur "epingle" pour un point personnalise, avec etiquette.
    private void paintCustomMarker(Graphics2D g2, double[] pt, Color c, String label, int w, int h) {
        int[] p = latLonToPixel(pt[0], pt[1], w, h);
        int px = p[0], py = p[1];
        if (px < -30 || px > w + 30 || py < -30 || py > h + 30) return;
        // Tige + tete de l'epingle
        g2.setColor(c);
        g2.setStroke(new BasicStroke(2.5f));
        g2.drawLine(px, py, px, py - 14);
        g2.setColor(Color.WHITE);
        g2.fillOval(px - 8, py - 26, 17, 17);
        g2.setColor(c);
        g2.fillOval(px - 7, py - 25, 15, 15);
        g2.setStroke(new BasicStroke(1f));
        // Etiquette
        g2.setFont(new Font("Segoe UI", Font.BOLD, 11));
        FontMetrics fm = g2.getFontMetrics();
        int tw = fm.stringWidth(label) + 12;
        g2.setColor(new Color(255, 255, 255, 220));
        g2.fillRoundRect(px + 12, py - 32, tw, 20, 6, 6);
        g2.setColor(c.darker());
        g2.drawString(label, px + 18, py - 18);
    }

    // ── Tile loading ─────────────────────────────────────────────

    private void requestTile(int z, int tx, int ty) {
        String key = z + "/" + tx + "/" + ty;
        if (tileCache.containsKey(key) || !pendingTiles.add(key)) return;
        tileLoader.submit(() -> {
            try {
                String urlStr = "https://tile.openstreetmap.org/" + key + ".png";
                HttpURLConnection conn = (HttpURLConnection) URI.create(urlStr).toURL().openConnection();
                conn.setRequestProperty("User-Agent", "MetroEfreiDodo/1.0 Java Swing educational");
                conn.setConnectTimeout(6000);
                conn.setReadTimeout(10000);
                BufferedImage img = ImageIO.read(conn.getInputStream());
                if (img != null) tileCache.put(key, img);
            } catch (Exception ignored) {
            } finally {
                pendingTiles.remove(key);
                SwingUtilities.invokeLater(this::repaint);
            }
        });
    }

    // ── Coordinate helpers ────────────────────────────────────────

    // Niveau de tuile entier le plus proche du zoom fractionnaire.
    private int tileZ() {
        return (int) Math.round(zoom);
    }

    // Taille d'affichage d'une tuile (256 px multiplie par le facteur fractionnaire).
    private double tileSize() {
        return 256.0 * Math.pow(2, zoom - tileZ());
    }

    private void clampCenter() {
        centerLat = Math.max(IDF_LAT_MIN, Math.min(IDF_LAT_MAX, centerLat));
        centerLon = Math.max(IDF_LON_MIN, Math.min(IDF_LON_MAX, centerLon));
    }

    /** Convertit une position ecran (pixels) en [lat, lon]. */
    public double[] screenToLatLon(int x, int y) {
        int tz = tileZ();
        double ts = tileSize();
        double tx = lonToTileX(centerLon, tz) + (x - getWidth()  / 2.0) / ts;
        double ty = latToTileY(centerLat, tz) + (y - getHeight() / 2.0) / ts;
        return new double[]{tileYToLat(ty, tz), tileXToLon(tx, tz)};
    }

    private int[] latLonToPixel(double lat, double lon, int w, int h) {
        int tz = tileZ();
        double ts = tileSize();
        double cTX = lonToTileX(centerLon, tz);
        double cTY = latToTileY(centerLat, tz);
        int px = (int) Math.round((lonToTileX(lon, tz) - cTX) * ts + w / 2.0);
        int py = (int) Math.round((latToTileY(lat, tz) - cTY) * ts + h / 2.0);
        return new int[]{px, py};
    }

    private int[] stationToPixel(MetroLoader.StationInfo s, int w, int h) {
        return latLonToPixel(s.lat, s.lon, w, h);
    }

    private static double lonToTileX(double lon, double zoom) {
        return (lon + 180.0) / 360.0 * Math.pow(2, zoom);
    }

    private static double latToTileY(double lat, double zoom) {
        double lr = Math.toRadians(lat);
        return (1.0 - Math.log(Math.tan(lr) + 1.0 / Math.cos(lr)) / Math.PI) / 2.0 * Math.pow(2, zoom);
    }

    private static double tileXToLon(double tx, double zoom) {
        return tx / Math.pow(2, zoom) * 360.0 - 180.0;
    }

    private static double tileYToLat(double ty, double zoom) {
        double n = Math.PI - 2.0 * Math.PI * ty / Math.pow(2, zoom);
        return Math.toDegrees(Math.atan(Math.sinh(n)));
    }
}
