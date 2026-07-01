import java.util.List;
import java.util.Map;

// Resultat de l'algo de plus court chemin (RAPTOR).

public class Journey {
    public final Map<String, String> arrivalTimes; // tous les arrets atteignables a partir de l'origine, avec heure d'arrivee la plus tot "HH:MM:SS"
    public final Map<String, Leg> legs;            // pour reconstruire le chemin de n'importe quel arret atteint
    public final String destStopId;                // arret de destination atteint au plus tot (null si injoignable) a voir avec la connexité
    public final String destArrivalTime;           // heure d'arrivee a destStopId "HH:MM:SS" (null si injoignable) pareil 
    public final String destTotalArrivalTime;      // arrivee estimee au POINT de destination = transit + marche finale "HH:MM:SS" (null si injoignable)
    public final int finalWalkSeconds;             // duree de la marche finale (destStopId -> point de destination), en secondes
    public final List<Leg> destPath;               // segments (depart -> arrivee) vers destStopId

    public Journey(Map<String, String> arrivalTimes, Map<String, Leg> legs,
            String destStopId, String destArrivalTime, String destTotalArrivalTime,
            int finalWalkSeconds, List<Leg> destPath) {
        this.arrivalTimes = arrivalTimes;
        this.legs = legs;
        this.destStopId = destStopId;
        this.destArrivalTime = destArrivalTime;
        this.destTotalArrivalTime = destTotalArrivalTime;
        this.finalWalkSeconds = finalWalkSeconds;
        this.destPath = destPath;
    }
}
