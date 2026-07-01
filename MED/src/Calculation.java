import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Array;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

public class Calculation {
    // Vitesse de marche a pied pour estimer le temps de marche final (m/s, ~5 km/h).
    private static final double WALK_SPEED_MPS = 1.4;

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

    // Temps de marche a pied entre deux points (en secondes), a WALK_SPEED_MPS.
    public static int walkSeconds(double lat1, double lon1, double lat2, double lon2) {
        return (int) Math.round(haversine(lat1, lon1, lat2, lon2) / WALK_SPEED_MPS);
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
    public static int toSeconds(String hms) { // Convertit "HH:MM:SS" en secondes depuis minuit. Retourne -1 si format invalide.
        if (hms == null || hms.isEmpty()) {
            return -1;
        }
        String[] p = hms.split(":");
        if (p.length != 3) {
            return -1;
        }
        try {
            return Integer.parseInt(p[0]) * 3600
                 + Integer.parseInt(p[1]) * 60
                 + Integer.parseInt(p[2]);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    // Reconvertit des secondes depuis minuit en "HH:MM:SS" (gere les heures >= 24).
    public static String fromSeconds(int totalSeconds) {
        int h = totalSeconds / 3600;
        int m = (totalSeconds % 3600) / 60;
        int s = totalSeconds % 60;
        return String.format("%02d:%02d:%02d", h, m, s);
    }

    // Round 0 : depuis les arrets d'origine (chacun atteint a originArrivals[stop] = startTime + marche
    // depuis le point de depart), on embarque un trip s'il part apres notre arrivee et dans la fenetre.
    public static Map<String, String> getRound0ArrivalTimes(Map<String, List<Stops_times>> tripSequences,
            Map<String, String> originArrivals, String startTime, int windowMinutes, Map<String, Leg> legs) {
        Map<String, String> bestArrivalTime = new HashMap<>();
        int endSeconds = toSeconds(startTime) + windowMinutes * 60;
        // Arrets d'origine : atteints a pied depuis le point de depart, sans leg (fin de reconstruction).
        bestArrivalTime.putAll(originArrivals);

        for (List<Stops_times> sequence : tripSequences.values()) {
            for (int i = 0; i < sequence.size(); i++) {
                Stops_times boarding = sequence.get(i);
                String originArr = originArrivals.get(boarding.getStop_id());
                if (originArr == null) {
                    continue;                       // pas un arret d'origine
                }
                // On embarque seulement si le trip part APRES notre arrivee a pied a l'arret,
                // et avant la fin de la fenetre.
                int depSeconds = toSeconds(boarding.getDeparture_time());
                if (depSeconds < toSeconds(originArr) || depSeconds > endSeconds) {
                    continue;
                }
                for (int j = i + 1; j < sequence.size(); j++) {
                    Stops_times reached = sequence.get(j);
                    String stopId = reached.getStop_id();
                    String arrival = reached.getArrival_time();
                    // On garde l'arrivee la plus tot pour chaque arret atteint.
                    // Les heures GTFS sont en "HH:MM:SS" zero-padde : compareTo = ordre chronologique.
                    if (!bestArrivalTime.containsKey(stopId)
                            || arrival.compareTo(bestArrivalTime.get(stopId)) < 0) {
                        bestArrivalTime.put(stopId, arrival);
                        legs.put(stopId, new Leg(boarding.getStop_id(), stopId, false, boarding.getTrip_id(),
                                boarding.getDeparture_time(), arrival));
                    }
                }
            }
        }
        return bestArrivalTime;
    }

    // Une ronde RAPTOR : depuis les arrets deja atteints , on embarque un nouveau trip
    // si on peut le prendre depart plus grand que arrivee + minTransfer), et on relache les arrivees .
    // Met a jour arrivalTimes en place, renvoie les arrets ameliores (= marked de la ronde suivante).
    public static Set<String> runRound(Map<String, List<Stops_times>> tripSequences,
            Map<String, String> arrivalTimes, Map<String, Leg> legs, Set<String> markedStops, int minTransferSeconds) {
        Set<String> improved = new HashSet<>();
        for (List<Stops_times> sequence : tripSequences.values()) {
            for (int i = 0; i < sequence.size(); i++) {
                Stops_times boarding = sequence.get(i);
                if (!markedStops.contains(boarding.getStop_id())) {
                    continue;                       // on n'embarque que depuis les arrets atteints au tour precedent
                }
                int readyAt = toSeconds(arrivalTimes.get(boarding.getStop_id())) + minTransferSeconds;
                int depSeconds = toSeconds(boarding.getDeparture_time());
                if (depSeconds < 0 || depSeconds < readyAt) {
                    continue;                       
                }
                for (int j = i + 1; j < sequence.size(); j++) {
                    Stops_times reached = sequence.get(j);
                    String stopId = reached.getStop_id();
                    String arrival = reached.getArrival_time();
                    if (arrival == null || arrival.isEmpty()) {
                        continue;
                    }
                    String best = arrivalTimes.get(stopId);
                    // "HH:MM:SS" zero-padde : compareTo = ordre chronologique
                    if (best == null || arrival.compareTo(best) < 0) {
                        arrivalTimes.put(stopId, arrival);
                        legs.put(stopId, new Leg(boarding.getStop_id(), stopId, false, boarding.getTrip_id(),
                                boarding.getDeparture_time(), arrival));
                        improved.add(stopId);
                    }
                }
            }
        }
        return improved;
    }

    // Phase correspondances : depuis les arrets ameliores, on "marche" vers les arrets en
    // correspondance. arrivee(to) = arrivee(from) + min_transfer_time (en secondes) si meilleur.
    // Met a jour arrivalTimes en place, renvoie les arrets nouvellement ameliores par la marche.
    public static Set<String> applyTransfers(Map<String, String> arrivalTimes, Map<String, Leg> legs,
            Set<String> improved, Map<String, List<Transfers>> transfersByFrom) {
        Set<String> walked = new HashSet<>();
        for (String from : improved) {
            List<Transfers> list = transfersByFrom.get(from);
            if (list == null) {
                continue;
            }
            String fromArrival = arrivalTimes.get(from);
            int fromSec = toSeconds(fromArrival);
            if (fromSec < 0) {
                continue;
            }
            for (Transfers t : list) {
                if ("3".equals(t.getTransfer_type())) {
                    continue;                       // correspondance impossible
                }
                int cost = 0;                       
                String mtt = t.getMin_transfer_time();
                if (mtt != null && !mtt.isEmpty()) {
                    try { cost = Integer.parseInt(mtt.trim()); } catch (NumberFormatException ignored) {}
                }
                String to = t.getTo_stop_id();
                String candidate = fromSeconds(fromSec + cost);
                String best = arrivalTimes.get(to);
                if (best == null || candidate.compareTo(best) < 0) {
                    arrivalTimes.put(to, candidate);
                    legs.put(to, new Leg(from, to, true, null, fromArrival, candidate));
                    walked.add(to);
                }
            }
        }
        return walked;
    }

    // Reconstruit l'itineraire d'un arret destination en remontant les legs jusqu'a l'origine.
    // Renvoie les segments dans l'ordre depart -> arrivee.
    public static List<Leg> reconstructPath(String destStop, Map<String, Leg> legs) {
        LinkedList<Leg> path = new LinkedList<>();
        Set<String> seen = new HashSet<>();
        String current = destStop;
        // seen.add renvoie false si on repasse par un arret deja vu -> garde-fou anti-boucle.
        while (legs.containsKey(current) && seen.add(current)) {
            Leg leg = legs.get(current);
            path.addFirst(leg);
            current = leg.fromStop;
        }
        return path;
    }

    // === ALGO PRINCIPAL ===
    // Entree : lat/lon origine, lat/lon destination, rayon origine (m), rayon destination (m),
    //          heure de depart "HH:MM:SS", fenetre de depart (min),
    //          tampon de correspondance au meme arret (s), nb max de changements.
    // Sortie : un Journey = meilleur arret de destination (stop_id) + le chemin (liste de Leg),
    //          plus tous les arrets atteignables et les legs pour reconstruire n'importe quel chemin.
    public static Journey findJourney(double originLat, double originLon,
            double destLat, double destLon, double originRadius, double destRadius,
            String startTime, int windowMinutes, int minTransferSeconds, int maxChangements) {

        // 1. Arrets de depart proches de l'origine, chacun atteint a startTime + marche (point -> arret)
        List<Stops> originStops = getNearbyStopsFromDB(originLat, originLon, originRadius);
        int startSec = toSeconds(startTime);
        Map<String, String> originArrivals = new HashMap<>();
        for (Stops s : originStops) {
            int walk = walkSeconds(originLat, originLon,
                    Double.parseDouble(s.getStop_lat()), Double.parseDouble(s.getStop_lon()));
            originArrivals.put(s.getStop_id(), fromSeconds(startSec + walk));
        }
        Set<String> startStopIds = originArrivals.keySet();

        // 2. Trips passant par ces arrets + leurs sequences d'arrets
        Map<String, List<Stops_times>> tripSequences = getTripSequences(getActiveTripIds(startStopIds));

        // 3. Round 0 (sans changement) puis correspondances a pied
        Map<String, Leg> legs = new HashMap<>();
        Map<String, String> arrivalTimes =
                getRound0ArrivalTimes(tripSequences, originArrivals, startTime, windowMinutes, legs);
        Set<String> marked = new HashSet<>(arrivalTimes.keySet());
        marked.addAll(applyTransfers(arrivalTimes, legs, marked, getTransfers(marked)));

        // 4. Rondes suivantes : chaque ronde ajoute une correspondance (un trip de plus)
        for (int round = 1; round <= maxChangements && !marked.isEmpty(); round++) {
            Map<String, List<Stops_times>> seq = getTripSequences(getActiveTripIds(marked));
            Set<String> improved = runRound(seq, arrivalTimes, legs, marked, minTransferSeconds);
            improved.addAll(applyTransfers(arrivalTimes, legs, improved, getTransfers(improved)));
            marked = improved;
        }

        // 5. Destination : parmi les arrets proches, on choisit celui qui minimise le TEMPS TOTAL
        //    = arrivee transit + marche finale (arret -> point de destination).
        List<Stops> destStops = getNearbyStopsFromDB(destLat, destLon, destRadius);
        String bestDest = null;
        String bestArrival = null;              // heure d'arrivee transit a l'arret choisi
        int bestTotal = Integer.MAX_VALUE;      // arrivee transit + marche finale, en secondes
        for (Stops s : destStops) {
            String arr = arrivalTimes.get(s.getStop_id());
            if (arr == null) {
                continue;                       // arret non atteint par le transit
            }
            int total = toSeconds(arr) + walkSeconds(destLat, destLon,
                    Double.parseDouble(s.getStop_lat()), Double.parseDouble(s.getStop_lon()));
            if (total < bestTotal) {
                bestTotal = total;
                bestDest = s.getStop_id();
                bestArrival = arr;
            }
        }
        String destTotal = (bestDest == null) ? null : fromSeconds(bestTotal);
        int finalWalkSeconds = (bestDest == null) ? 0 : bestTotal - toSeconds(bestArrival);
        List<Leg> destPath = (bestDest == null) ? new ArrayList<>() : reconstructPath(bestDest, legs);
        return new Journey(arrivalTimes, legs, bestDest, bestArrival, destTotal, finalWalkSeconds, destPath);
    }

    // Requete SQL : SEULEMENT les arrets dans le rayon, filtres cote SQL par une
    // bounding box (carre lat/lon) puis affines au cercle exact par haversine en Java.
    // Evite de charger toute la table stops puis de filtrer cote Java donc meilleur performance.
    public static List<Stops> getNearbyStopsFromDB(double lat, double lon, double radiusMeters) {
        List<Stops> result = new ArrayList<>();

        // Bounding box en degres autour du point.
        double dLat = radiusMeters / 111320.0;                                   // ~ metres par degre de latitude
        double dLon = radiusMeters / (111320.0 * Math.cos(Math.toRadians(lat))); // corrige par la latitude

        // stop_lat / stop_lon sont des colonnes numeriques : comparaison directe, sans cast.
        // Les lignes a lat/lon NULL ne matchent pas le BETWEEN (ecartees sans erreur).
        String sql = "SELECT stop_id, stop_code, stop_name, stop_desc, stop_lon, stop_lat, "
                   + "zone_id, stop_url, location_type, parent_station, stop_timezone, "
                   + "level_id, wheelchair_boarding, platform_code FROM stops "
                   + "WHERE stop_lat BETWEEN ? AND ? "
                   + "AND stop_lon BETWEEN ? AND ?";
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setDouble(1, lat - dLat);
            ps.setDouble(2, lat + dLat);
            ps.setDouble(3, lon - dLon);
            ps.setDouble(4, lon + dLon);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String[] row = new String[14];
                    for (int i = 0; i < 14; i++) {
                        row[i] = rs.getString(i + 1);
                    }
                    Stops s = new Stops(row);
                    // La box est un carre : on garde uniquement le vrai cercle.
                    double d = haversine(lat, lon,
                            Double.parseDouble(s.getStop_lat()),
                            Double.parseDouble(s.getStop_lon()));
                    if (d <= radiusMeters) {
                        result.add(s);
                    }
                }
            }
        } catch (SQLException e) {
            System.err.println("Erreur SQL (getNearbyStopsFromDB) : " + e.getMessage());
        }
        return result;
    }

    // Requete SQL : les correspondances au depart des arrets donnes, indexees par from_stop_id.
    // from_stop_id = ANY(?) : on ne charge que les transferts utiles (une seule requete batchee).
    public static Map<String, List<Transfers>> getTransfers(Set<String> fromStopIds) {
        Map<String, List<Transfers>> transfersByFrom = new HashMap<>();
        if (fromStopIds.isEmpty()) {
            return transfersByFrom;
        }
        String sql = "SELECT from_stop_id, to_stop_id, transfer_type, min_transfer_time "
                   + "FROM transfers WHERE from_stop_id = ANY(?)";
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            Array ids = conn.createArrayOf("text", fromStopIds.toArray());
            ps.setArray(1, ids);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String[] row = new String[4];
                    for (int i = 0; i < 4; i++) {
                        row[i] = rs.getString(i + 1);
                    }
                    transfersByFrom.computeIfAbsent(row[0], k -> new ArrayList<>())
                                   .add(new Transfers(row));
                }
            }
        } catch (SQLException e) {
            System.err.println("Erreur SQL (getTransfers) : " + e.getMessage());
        }
        return transfersByFrom;
    }
}

