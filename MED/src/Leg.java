// Un segment d'itineraire : comment on est arrive a un arret (pour le reconstruire plus tard).
// aPied = true quand le segment est une correspondance a pied (transfer, sans vehicule).
public class Leg {
    public final String fromStop;    // arret d'embarquement (ou de depart de la marche)
    public final String toStop;      // arret atteint par ce segment
    public final boolean aPied;      // true = correspondance a pied ; false = on roule dans un trip
    public final String tripId;      // trip emprunte quand on roule ; null quand aPied == true
    public final String departTime;  // heure de depart a fromStop ("HH:MM:SS")
    public final String arriveTime;  // heure d'arrivee a toStop ("HH:MM:SS")

    public Leg(String fromStop, String toStop, boolean aPied, String tripId, String departTime, String arriveTime) {
        this.fromStop = fromStop;
        this.toStop = toStop;
        this.aPied = aPied;
        this.tripId = tripId;
        this.departTime = departTime;
        this.arriveTime = arriveTime;
    }
}
