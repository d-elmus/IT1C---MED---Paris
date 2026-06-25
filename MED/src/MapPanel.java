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
    private int zoom = 12;

    private List<MetroLoader.StationInfo> stations = new ArrayList<>();
    private final Map<String, BufferedImage> tileCache = new ConcurrentHashMap<>();
    private final Set<String> pendingTiles = ConcurrentHashMap.newKeySet();
    private final ExecutorService tileLoader = Executors.newFixedThreadPool(4);

    private Point dragStartScreen;
    private double dragStartLat, dragStartLon;
    private MetroLoader.StationInfo hoveredStation;

    // Callback invoked when user clicks a station marker
    private java.util.function.Consumer<MetroLoader.StationInfo> onStationClick;

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
                    if (dist < 5 && hoveredStation != null && onStationClick != null) {
                        onStationClick.accept(hoveredStation);
                    }
                }
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                if (dragStartScreen == null) return;
                double dx = e.getX() - dragStartScreen.x;
                double dy = e.getY() - dragStartScreen.y;
                double newTX = lonToTileX(dragStartLon, zoom) - dx / 256.0;
                double newTY = latToTileY(dragStartLat, zoom) - dy / 256.0;
                centerLon = tileXToLon(newTX, zoom);
                centerLat = tileYToLat(newTY, zoom);
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
                    setCursor(nearest != null
                            ? Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR)
                            : Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                    repaint();
                }
            }
        };
        addMouseListener(ma);
        addMouseMotionListener(ma);
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        // Mouse wheel zoom
        addMouseWheelListener(e -> {
            // Zoom toward mouse position
            int mx = (int) e.getX(), my = (int) e.getY();
            int w = getWidth(), h = getHeight();
            double mouseTX = lonToTileX(centerLon, zoom) + (mx - w / 2.0) / 256.0;
            double mouseTY = latToTileY(centerLat, zoom) + (my - h / 2.0) / 256.0;

            int newZoom = zoom - (int) Math.signum(e.getPreciseWheelRotation());
            newZoom = Math.max(3, Math.min(18, newZoom));
            if (newZoom == zoom) return;

            // Adjust center so the tile under cursor stays fixed
            double ratio = Math.pow(2, newZoom - zoom);
            double newCenterTX = mouseTX * ratio - (mx - w / 2.0) / 256.0;
            double newCenterTY = mouseTY * ratio - (my - h / 2.0) / 256.0;
            centerLon = tileXToLon(newCenterTX, newZoom);
            centerLat = tileYToLat(newCenterTY, newZoom);
            zoom = newZoom;
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

    /** Center the map on a named station. */
    public void focusStation(String name) {
        for (MetroLoader.StationInfo s : stations) {
            if (s.name.equals(name)) {
                centerLat = s.lat;
                centerLon = s.lon;
                if (zoom < 14) zoom = 14;
                repaint();
                return;
            }
        }
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
        double cTX = lonToTileX(centerLon, zoom);
        double cTY = latToTileY(centerLat, zoom);
        int maxTile = (1 << zoom) - 1;

        int x0 = (int) Math.floor(cTX - w / 512.0) - 1;
        int x1 = (int) Math.floor(cTX + w / 512.0) + 1;
        int y0 = (int) Math.floor(cTY - h / 512.0) - 1;
        int y1 = (int) Math.floor(cTY + h / 512.0) + 1;

        for (int ty = y0; ty <= y1; ty++) {
            for (int tx = x0; tx <= x1; tx++) {
                if (tx < 0 || ty < 0 || tx > maxTile || ty > maxTile) continue;
                int px = (int) Math.round((tx - cTX) * 256 + w / 2.0);
                int py = (int) Math.round((ty - cTY) * 256 + h / 2.0);
                String key = zoom + "/" + tx + "/" + ty;
                BufferedImage tile = tileCache.get(key);
                if (tile != null) {
                    g2.drawImage(tile, px, py, 256, 256, null);
                } else {
                    g2.setColor(new Color(220, 220, 220));
                    g2.fillRect(px, py, 256, 256);
                    g2.setColor(new Color(190, 190, 190));
                    g2.drawRect(px, py, 256, 256);
                    requestTile(zoom, tx, ty);
                }
            }
        }

        // Station markers
        for (MetroLoader.StationInfo s : stations) {
            int[] p = stationToPixel(s, w, h);
            int px = p[0], py = p[1];
            if (px < -15 || px > w + 15 || py < -15 || py > h + 15) continue;
            g2.setColor(Color.WHITE);
            g2.fillOval(px - 6, py - 6, 13, 13);
            g2.setColor(new Color(227, 5, 28));
            g2.fillOval(px - 5, py - 5, 11, 11);
        }

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
        String zoomStr = "Zoom " + zoom;
        g2.fillRoundRect(8, h - 24, fm.stringWidth(zoomStr) + 12, 18, 4, 4);
        g2.setColor(new Color(50, 50, 50));
        g2.drawString(zoomStr, 14, h - 9);

        g2.dispose();
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

    private int[] stationToPixel(MetroLoader.StationInfo s, int w, int h) {
        double cTX = lonToTileX(centerLon, zoom);
        double cTY = latToTileY(centerLat, zoom);
        int px = (int) Math.round((lonToTileX(s.lon, zoom) - cTX) * 256 + w / 2.0);
        int py = (int) Math.round((latToTileY(s.lat, zoom) - cTY) * 256 + h / 2.0);
        return new int[]{px, py};
    }

    private static double lonToTileX(double lon, int zoom) {
        return (lon + 180.0) / 360.0 * (1 << zoom);
    }

    private static double latToTileY(double lat, int zoom) {
        double lr = Math.toRadians(lat);
        return (1.0 - Math.log(Math.tan(lr) + 1.0 / Math.cos(lr)) / Math.PI) / 2.0 * (1 << zoom);
    }

    private static double tileXToLon(double tx, int zoom) {
        return tx / (1 << zoom) * 360.0 - 180.0;
    }

    private static double tileYToLat(double ty, int zoom) {
        double n = Math.PI - 2.0 * Math.PI * ty / (1 << zoom);
        return Math.toDegrees(Math.atan(Math.sinh(n)));
    }
}
