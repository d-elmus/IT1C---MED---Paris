//trip_id,arrival_time,departure_time,stop_id,stop_sequence,pickup_type,drop_off_type,local_zone_id,stop_headsign,timepoint
public class Stops_times {

    private String trip_id;
    private int stop_sequence;
    private String arrival_time;
    private String stop_id;
    private String departure_time;
    private boolean pickup_type;
    private boolean drop_off_type;
    private int local_zone_id;
    private String stop_headsign;
    private boolean timepoint;

    public Stops_times(String trip_id, int stop_sequence, String arrival_time, String stop_id, String departure_time, boolean pickup_type, boolean drop_off_type,
                     int local_zone_id, String stop_headsign, boolean timepoint) {

        this.trip_id = trip_id;
        this.stop_sequence = stop_sequence;
        this.arrival_time = arrival_time;
        this.stop_id = stop_id;
        this.departure_time = departure_time;
        this.pickup_type = pickup_type;
        this.drop_off_type = drop_off_type;
        this.local_zone_id = local_zone_id;
        this.stop_headsign = stop_headsign;
        this.timepoint = timepoint;
    }

    @Override
    public String toString() {
        return "Stops_times{" +
                "trip_id='" + trip_id + '\'' +
                ", arrival_time='" + arrival_time + '\'' +
                ", departure_time='" + departure_time + '\'' +
                ", stop_id='" + stop_id + '\'' +
                ", stop_sequence='" + stop_sequence + '\'' +
                ", pickup_type='" + pickup_type + '\'' +
                ", drop_off_type='" + drop_off_type + '\'' +
                ", local_zone_id='" + local_zone_id + '\'' +
                ", stop_headsign='" + stop_headsign + '\'' +
                ", timepoint='" + timepoint + '\'' +
                '}';
    }

    
}
