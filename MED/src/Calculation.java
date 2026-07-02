// MED/src/Calculation.java
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class Calculation {
    // Haversine function pout donner la distance entre deux points avec lat et lon : Found on internet
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

    /*public static List<Stops> getNearbyStops(double lat1, double lon1, List<Stops> allStops, double radiusMeters) {
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
*/
 
}

