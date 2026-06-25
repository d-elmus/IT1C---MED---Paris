import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

public class MetroLoader {

    private static final String ROUTES_FILE    = "routes.txt";
    private static final String TRIPS_FILE     = "trips.txt";
    private static final String STOPS_FILE     = "stops.txt";
    private static final String STOP_TIMES_FILE = "stop_times.txt";
    private static final String CACHE_FILE     = "cache/stations_metro.json";

    public static class StationInfo {
        public final String name;
        public final double lat;
        public final double lon;

        public StationInfo(String name, double lat, double lon) {
            this.name = name;
            this.lat  = lat;
            this.lon  = lon;
        }
    }

    private static class StopData {
        final String name;
        final double lat, lon;
        final String parentStation;

        StopData(String name, double lat, double lon, String parentStation) {
            this.name          = name;
            this.lat           = lat;
            this.lon           = lon;
            this.parentStation = parentStation;
        }
    }

    // ---------------------------------------------------------------

    public List<StationInfo> loadMetroStations() throws IOException {
        Path cachePath = Path.of(CACHE_FILE);
        if (Files.exists(cachePath)) {
            System.out.println("Cache trouvé → " + CACHE_FILE);
            return loadFromCache(cachePath);
        }

        System.out.println("Extraction GTFS (première fois, peut prendre ~10 s)…");

        // 1. routes.txt → route_ids où route_type == "1" (métro)
        Set<String> metroRouteIds = loadMetroRouteIds();
        System.out.println("  routes métro   : " + metroRouteIds.size());

        // 2. trips.txt → trip_ids pour ces routes
        Set<String> metroTripIds = loadMetroTripIds(metroRouteIds);
        System.out.println("  trips métro    : " + metroTripIds.size());

        // 3. stops.txt entier en mémoire (~5 MB)
        Map<String, StopData> allStops = loadAllStops();
        System.out.println("  arrêts chargés : " + allStops.size());

        // 4. stop_times.txt en STREAMING ligne par ligne (676 MB)
        Set<String> metroStationIds = streamStopTimes(metroTripIds, allStops);
        System.out.println("  stations parent: " + metroStationIds.size());

        // 5. Construction de la liste finale, dédupliquée par nom
        List<StationInfo> stations = buildStationList(metroStationIds, allStops);
        System.out.println("  stations uniques: " + stations.size());

        // 6. Écriture cache
        Files.createDirectories(Path.of("cache"));
        writeCache(cachePath, stations);
        System.out.println("  cache écrit → " + CACHE_FILE);

        return stations;
    }

    // ---------------------------------------------------------------
    // Étapes d'extraction

    private Set<String> loadMetroRouteIds() throws IOException {
        // route_id[0], route_type[5]
        Set<String> ids = new HashSet<>();
        try (BufferedReader br = utf8Reader(ROUTES_FILE)) {
            br.readLine(); // header
            String line;
            while ((line = br.readLine()) != null) {
                String[] f = line.split(",", -1);
                if (f.length > 5 && "1".equals(f[5].trim())) {
                    ids.add(f[0].trim());
                }
            }
        }
        return ids;
    }

    private Set<String> loadMetroTripIds(Set<String> metroRouteIds) throws IOException {
        // route_id[0], trip_id[2]
        Set<String> ids = new HashSet<>();
        try (BufferedReader br = utf8Reader(TRIPS_FILE)) {
            br.readLine(); // header
            String line;
            while ((line = br.readLine()) != null) {
                String[] f = line.split(",", -1);
                if (f.length > 2 && metroRouteIds.contains(f[0].trim())) {
                    ids.add(f[2].trim());
                }
            }
        }
        return ids;
    }

    private Map<String, StopData> loadAllStops() throws IOException {
        // stop_id[0], stop_name[2], stop_lon[4], stop_lat[5],
        // location_type[8], parent_station[9]
        Map<String, StopData> map = new HashMap<>();
        try (BufferedReader br = utf8Reader(STOPS_FILE)) {
            br.readLine(); // header
            String line;
            while ((line = br.readLine()) != null) {
                String[] f = line.split(",", -1);
                if (f.length < 10) continue;
                String lonStr = f[4].trim();
                String latStr = f[5].trim();
                if (lonStr.isEmpty() || latStr.isEmpty()) continue;
                try {
                    double lat = Double.parseDouble(latStr);
                    double lon = Double.parseDouble(lonStr);
                    map.put(f[0].trim(),
                            new StopData(f[2].trim(), lat, lon, f[9].trim()));
                } catch (NumberFormatException ignored) {}
            }
        }
        return map;
    }

    private Set<String> streamStopTimes(Set<String> metroTripIds,
                                        Map<String, StopData> allStops) throws IOException {
        // trip_id[0], stop_id[3]
        // Lecture avec un grand buffer pour la performance (676 MB)
        Set<String> stationIds = new HashSet<>();
        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(
                        new FileInputStream(STOP_TIMES_FILE), StandardCharsets.UTF_8),
                1 << 16)) { // buffer 64 KB
            br.readLine(); // header
            String line;
            while ((line = br.readLine()) != null) {
                // Extraction manuelle : évite split() qui crée un tableau inutile
                int c0 = line.indexOf(',');
                if (c0 < 0) continue;
                if (!metroTripIds.contains(line.substring(0, c0))) continue;

                // Sauter champ 1 (arrival_time) et champ 2 (departure_time)
                int c1 = line.indexOf(',', c0 + 1);
                if (c1 < 0) continue;
                int c2 = line.indexOf(',', c1 + 1);
                if (c2 < 0) continue;
                int c3 = line.indexOf(',', c2 + 1);
                String stopId = (c3 < 0)
                        ? line.substring(c2 + 1)
                        : line.substring(c2 + 1, c3);

                StopData stop = allStops.get(stopId);
                if (stop == null) continue;
                // Utiliser le parent (bâtiment de gare) ou l'arrêt lui-même
                String parentId = stop.parentStation.isEmpty() ? stopId : stop.parentStation;
                stationIds.add(parentId);
            }
        }
        return stationIds;
    }

    private List<StationInfo> buildStationList(Set<String> stationIds,
                                               Map<String, StopData> allStops) {
        Map<String, StationInfo> byName = new LinkedHashMap<>();
        for (String id : stationIds) {
            StopData d = allStops.get(id);
            if (d == null || d.name.isEmpty()) continue;
            byName.putIfAbsent(d.name, new StationInfo(d.name, d.lat, d.lon));
        }
        List<StationInfo> list = new ArrayList<>(byName.values());
        list.sort(Comparator.comparing(s -> s.name));
        return list;
    }

    // ---------------------------------------------------------------
    // Cache JSON (format simple, sans dépendance externe)

    private void writeCache(Path path, List<StationInfo> stations) throws IOException {
        StringBuilder sb = new StringBuilder("[\n");
        for (int i = 0; i < stations.size(); i++) {
            StationInfo s = stations.get(i);
            sb.append("  {\"name\":\"")
              .append(s.name.replace("\\", "\\\\").replace("\"", "\\\""))
              .append("\",\"lat\":").append(s.lat)
              .append(",\"lon\":").append(s.lon)
              .append("}");
            if (i < stations.size() - 1) sb.append(",");
            sb.append("\n");
        }
        sb.append("]");
        Files.writeString(path, sb.toString(), StandardCharsets.UTF_8);
    }

    private List<StationInfo> loadFromCache(Path cachePath) throws IOException {
        String json = Files.readString(cachePath, StandardCharsets.UTF_8);
        List<StationInfo> list = new ArrayList<>();
        int pos = 0;
        while (pos < json.length()) {
            int start = json.indexOf('{', pos);
            if (start < 0) break;
            int end = json.indexOf('}', start);
            if (end < 0) break;
            String obj = json.substring(start + 1, end);
            String  name = extractString(obj, "name");
            Double  lat  = extractDouble(obj, "lat");
            Double  lon  = extractDouble(obj, "lon");
            if (name != null && lat != null && lon != null) {
                list.add(new StationInfo(name, lat, lon));
            }
            pos = end + 1;
        }
        return list;
    }

    private String extractString(String obj, String key) {
        String marker = "\"" + key + "\":\"";
        int i = obj.indexOf(marker);
        if (i < 0) return null;
        i += marker.length();
        StringBuilder sb = new StringBuilder();
        while (i < obj.length()) {
            char c = obj.charAt(i);
            if (c == '\\' && i + 1 < obj.length()) { sb.append(obj.charAt(i + 1)); i += 2; }
            else if (c == '"') break;
            else { sb.append(c); i++; }
        }
        return sb.toString();
    }

    private Double extractDouble(String obj, String key) {
        String marker = "\"" + key + "\":";
        int i = obj.indexOf(marker);
        if (i < 0) return null;
        i += marker.length();
        int end = i;
        while (end < obj.length()) {
            char c = obj.charAt(end);
            if (Character.isDigit(c) || c == '.' || c == '-') end++;
            else break;
        }
        if (end == i) return null;
        try { return Double.parseDouble(obj.substring(i, end)); }
        catch (NumberFormatException e) { return null; }
    }

    private BufferedReader utf8Reader(String filename) throws FileNotFoundException {
        return new BufferedReader(
                new InputStreamReader(new FileInputStream(filename), StandardCharsets.UTF_8));
    }
}
