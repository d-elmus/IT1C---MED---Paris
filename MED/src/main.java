import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.nio.file.*;
import java.util.List;

public class main {
    public static void main(String[] args) {
        /*
        Path path = Path.of("Data/agency.txt");
        List<Agency> agencies = new ArrayList<>();

        try {
            List<String> lines = Files.readAllLines(path);
            if (!lines.isEmpty()) {
                lines.remove(0);
            }

            for (String line : lines) {
                agencies.add(new Agency(line.split(",", -1)));
            }
            System.out.println("Nombre d'agences chargées : " + agencies.size());
            for (Agency agency : agencies) {
                System.out.println(agency);
            }

        } catch (IOException e) {
            System.err.println("An error occurred while reading the file: " + e.getMessage());
        }
        */
       // try with villejuif lat and lon 48.7927° N, 2.3593° E
       String filepath = System.getProperty("user.home") + "/Documents/Data_ITC/IDFM-gtfs/stops.txt";
       double lat = 48.7927;
       double lon = 2.3593;
       List<Stops> allStops = Calculation.getStopsfromFile(filepath);
       List<Stops> nearbyStops = Calculation.getNearbyStops(lat, lon, allStops, 500);
       System.out.println("Nombre d'arrêts à proximité : " + nearbyStops.size());
        for (Stops stop : nearbyStops) {
            System.out.println(stop);
        }
    }
}
