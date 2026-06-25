import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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

    // sera remplace par une requete SQL (SELECT DISTINCT trip_id ...
    public static Set<String> getActiveTripIds(String filePath, Set<String> nearbyStopIds) {
        Set<String> activeTripIds = new HashSet<>();
        Path path = Path.of(filePath);
        try (Stream<String> lines = Files.lines(path)) {
            lines.skip(1)
                 .forEach(line -> {
                     String[] fields = line.split(",", -1);
                     String stopId = fields[3];
                     if (nearbyStopIds.contains(stopId)) {
                         activeTripIds.add(fields[0]);
                     }
                 });
        } catch (IOException e) {
            System.err.println("An error occurred while reading the file: " + e.getMessage());
        }
        return activeTripIds;
    }

    // sera remplace par une requete SQL (SELECT * FROM stop_times WHERE ...
    public static Map<String, List<Stops_times>> getTripSequences(String filePath, Set<String> activeTripIds) {
        Map<String, List<Stops_times>> tripSequences = new HashMap<>();
        Path path = Path.of(filePath);
        try (Stream<String> lines = Files.lines(path)) {
            lines.skip(1)
                 .forEach(line -> {
                     String[] fields = line.split(",", -1);
                     String tripId = fields[0];
                     if (activeTripIds.contains(tripId)) {
                         tripSequences.computeIfAbsent(tripId, k -> new ArrayList<>()).add(new Stops_times(fields));
                     }
                 });
        } catch (IOException e) {
            System.err.println("An error occurred while reading the file: " + e.getMessage());
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

    // sera remplace par une requete SQL (SELECT * FROM stops)
    public static List<Stops> getStopsfromFile(String filePath) {
        List<Stops> stops = new ArrayList<>();
        Path path = Path.of(filePath);
        try {
            List<String> lines = Files.readAllLines(path);
            if (!lines.isEmpty()) {
                lines.remove(0); 
            }
            for (String line : lines) {
                stops.add(new Stops(line.split(",", -1)));
            }
        } catch (IOException e) {
            System.err.println("An error occurred while reading the file: " + e.getMessage());
        }

        return stops;
    }

 
}

