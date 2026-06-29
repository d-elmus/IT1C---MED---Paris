import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Array;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

public class Calculation {
    // Haversine function pour donner la distance entre deux points avec lat et lon : Found on internet
    public static double haversine(double lat1, double lon1, double lat2, double lon2) {
        final double R = 6371000;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                 + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                 * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return R * c;
    }

    public static List<Stops> getNearbyStops(double lat1, double lon1, List<Stops> allStops, double radiusMeters) {
        List<Stops> result = new ArrayList<>();
        for (Stops s : allStops) {
            String stopLat = s.getStop_lat();
            String stopLon = s.getStop_lon();
            if (stopLat == null || stopLat.isEmpty() || stopLon == null || stopLon.isEmpty()) {
                continue;
            }
            double d = haversine(lat1, lon1,
                    Double.parseDouble(stopLat),
                    Double.parseDouble(stopLon));
            if (d <= radiusMeters) {
                if (result.contains(s)) {// a verifier si ça marche car plusieurs stops d'une meme ligne peuvent etre sur un rayon donné
                    //si le stop est deja la on garde ou ajoute seuelement celui qui a la plus petite distance
                    Stops existingStop = result.get(result.indexOf(s));
                    double existingDistance = haversine(lat1, lon1,
                            Double.parseDouble(existingStop.getStop_lat()),
                            Double.parseDouble(existingStop.getStop_lon()));
                    if (d < existingDistance) {
                        result.remove(existingStop);
                        result.add(s);
                    } else {
                        continue;
                    }
                    
                }
                result.add(s);
            }
        }
        
        return result;
    }
// converti en Set plus facile pour chercher stop_times
    public static Set<String> getStopIds(List<Stops> stops) {
        Set<String> stopIds = new HashSet<>();
        for (Stops s : stops) {
            stopIds.add(s.getStop_id());
        }
        return stopIds;
    }

    // Requete SQL : tous les trips qui passent par un des arrets proches.
    // stop_id = ANY(?) remplace le balayage complet de stop_times.txt.
    public static Set<String> getActiveTripIds(Set<String> nearbyStopIds) {
        Set<String> activeTripIds = new HashSet<>();
        if (nearbyStopIds.isEmpty()) {
            return activeTripIds;
        }
        String sql = "SELECT DISTINCT trip_id FROM stop_times WHERE stop_id = ANY(?)";
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            Array ids = conn.createArrayOf("text", nearbyStopIds.toArray());
            ps.setArray(1, ids);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    activeTripIds.add(rs.getString("trip_id"));
                }
            }
        } catch (SQLException e) {
            System.err.println("Erreur SQL (getActiveTripIds) : " + e.getMessage());
        }
        return activeTripIds;
    }

    // Requete SQL : toutes les lignes stop_times des trips actifs.
    // Le tri par stop_sequence est fait en Java 
    public static Map<String, List<Stops_times>> getTripSequences(Set<String> activeTripIds) {
        Map<String, List<Stops_times>> tripSequences = new HashMap<>();
        if (activeTripIds.isEmpty()) {
            return tripSequences;
        }
        String sql = "SELECT trip_id, arrival_time, departure_time, stop_id, stop_sequence, "
                   + "pickup_type, drop_off_type, local_zone_id, stop_headsign, timepoint "
                   + "FROM stop_times WHERE trip_id = ANY(?)";
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            Array ids = conn.createArrayOf("text", activeTripIds.toArray());
            ps.setArray(1, ids);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String[] row = new String[10];
                    for (int i = 0; i < 10; i++) {
                        row[i] = rs.getString(i + 1); 
                    }
                    tripSequences.computeIfAbsent(row[0], k -> new ArrayList<>())
                                 .add(new Stops_times(row));
                }
            }
        } catch (SQLException e) {
            System.err.println("Erreur SQL (getTripSequences) : " + e.getMessage());
        }

        for (List<Stops_times> sequence : tripSequences.values()) {
            sequence.sort(Comparator.comparingInt(Stops_times::getStop_sequence));
        }
        return tripSequences;
    }

    public static Map<String, String> getRound0ArrivalTimes(Map<String, List<Stops_times>> tripSequences, Set<String> startStopIds) {
        Map<String, String> bestArrivalTime = new HashMap<>();
        for (List<Stops_times> sequence : tripSequences.values()) {
            for (int i = 0; i < sequence.size(); i++) {
                if (!startStopIds.contains(sequence.get(i).getStop_id())) {
                    continue;
                }
                for (int j = i + 1; j < sequence.size(); j++) {
                    Stops_times reached = sequence.get(j);
                    String stopId = reached.getStop_id();
                    String arrival = reached.getArrival_time();
                    if (!bestArrivalTime.containsKey(stopId) || arrival.compareTo(bestArrivalTime.get(stopId)) < 0) {
                        bestArrivalTime.put(stopId, arrival);
                    }
                }
            }
        }
        return bestArrivalTime;
    }

    // Requete SQL : tous les arrets. L'ordre des colonnes correspond au
    // constructeur Stops(String[]) pour pouvoir le reutiliser tel quel.
    public static List<Stops> getStopsFromDB() {
        List<Stops> stops = new ArrayList<>();
        String sql = "SELECT stop_id, stop_code, stop_name, stop_desc, stop_lon, stop_lat, "
                   + "zone_id, stop_url, location_type, parent_station, stop_timezone, "
                   + "level_id, wheelchair_boarding, platform_code FROM stops";
        try (Connection conn = Database.getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                String[] row = new String[14];
                for (int i = 0; i < 14; i++) {
                    row[i] = rs.getString(i + 1); 
                }
                stops.add(new Stops(row));
            }
        } catch (SQLException e) {
            System.err.println("Erreur SQL (getStopsFromDB) : " + e.getMessage());
        }
        return stops;
    }

}

