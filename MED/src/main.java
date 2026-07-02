public class main {
    public static void main(String[] args) {
         /*
        // Use system look-and-feel for native buttons/scrollbars
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {}

        SwingUtilities.invokeLater(() -> {
            AppWindow window = new AppWindow();
            window.setVisible(true);
            window.startLoading(); // loads GTFS in background thread
        });
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
       // test de l'algo , apres on aura juste la function a appeler
       double lat = 48.7927;        // origine (ex Villejuif)
       double lon = 2.3593;
       double destLat = 48.8583;    // destination (ex Chatelet)
       double destLon = 2.3470;
       double originRadius = 2000;  // rayon autour de l'origine (grand : plein d'arrets de depart possibles)
       double destRadius = 500;     // rayon autour de la destination (petit : on veut arriver pres du but)
       String startTime = "08:00:00";
       int windowMinutes = 30;      // ne garder que les trips partant dans cette fenetre
       int minTransfer = 0;         // tampon au meme arret (les vrais temps de marche sont dans transfers)
       int maxChangements = 3;      // nombre max de correspondances a voir avec la connexité
       int horizonMinutes = 120;    // budget temps total : ne charge que les trips partant dans ce delai

       Journey journey = Calculation.findJourney(lat, lon, destLat, destLon, originRadius, destRadius,
               startTime, windowMinutes, minTransfer, maxChangements, horizonMinutes);

       // === Sortie (affichage de test) ===
       System.out.println("Arrets atteignables : " + journey.arrivalTimes.size());
       if (journey.destStopId == null) {
           System.out.println("Aucun trajet trouve (augmenter maxChangements, la fenetre ou le rayon).");
       } else {
           System.out.println("Destination : " + journey.destStopId + " a " + journey.destArrivalTime);
           System.out.println("Arrivee estimee au point de destination : " + journey.destTotalArrivalTime
                   + "  (dont " + (journey.finalWalkSeconds / 60) + " min de marche finale)");
           System.out.println("Itineraire (" + journey.destPath.size() + " segments) :");
           for (Leg leg : journey.destPath) {
               String mode = leg.aPied ? "marche a pied" : "trip " + leg.tripId;
               System.out.println("  " + leg.fromStop + " (" + leg.departTime + ")  --" + mode
                       + "-->  " + leg.toStop + " (" + leg.arriveTime + ")");
           }
       }
    }
}
