//trip_id,arrival_time,departure_time,stop_id,stop_sequence,pickup_type,drop_off_type,local_zone_id,stop_headsign,timepoint
public class Stops_times {
    private String trip_id;
    private String arrival_time;
    private String departure_time;
    private String stop_id;
    private String stop_sequence;
    private String pickup_type;
    private String drop_off_type;
    private String local_zone_id;
    private String stop_headsign;
    private String timepoint;

    public Stops_times(String[] stops_times) {
        this.trip_id = stops_times[0];
        this.arrival_time = stops_times[1];
        this.departure_time = stops_times[2];
        this.stop_id = stops_times[3];
        this.stop_sequence = stops_times[4];
        this.pickup_type = stops_times[5];
        this.drop_off_type = stops_times[6];
        this.local_zone_id = stops_times[7];
        this.stop_headsign = stops_times[8];
        this.timepoint = stops_times[9];
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
