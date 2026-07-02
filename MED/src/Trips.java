//route_id,service_id,trip_id,trip_headsign,trip_short_name,direction_id,block_id,shape_id,wheelchair_accessible,bikes_allowed
public class Trips {

    private int trip_id;
    private String route_id;
    private int service_id;
    private boolean direction_id;
    private int block_id;
    private int shape_id;
    private String trip_short_name;
    private String trip_headsign;
    private boolean wheelchair_accessible;
    private boolean bikes_allowed;

    public Trips(int trip_id, String route_id, int service_id,
                 boolean direction_id, int block_id, int shape_id,
                 String trip_short_name, String trip_headsign,
                 boolean wheelchair_accessible, boolean bikes_allowed) {

        this.trip_id = trip_id;
        this.route_id = route_id;
        this.service_id = service_id;
        this.direction_id = direction_id;
        this.block_id = block_id;
        this.shape_id = shape_id;
        this.trip_short_name = trip_short_name;
        this.trip_headsign = trip_headsign;
        this.wheelchair_accessible = wheelchair_accessible;
        this.bikes_allowed = bikes_allowed;
    }
    @Override
    public String toString() {
        return "Trips{" +
                "route_id='" + route_id + '\'' +
                ", service_id='" + service_id + '\'' +
                ", trip_id='" + trip_id + '\'' +
                ", trip_headsign='" + trip_headsign + '\'' +
                ", trip_short_name='" + trip_short_name + '\'' +
                ", direction_id='" + direction_id + '\'' +
                ", block_id='" + block_id + '\'' +
                ", shape_id='" + shape_id + '\'' +
                ", wheelchair_accessible='" + wheelchair_accessible + '\'' +
                ", bikes_allowed='" + bikes_allowed + '\'' +
                '}';
    }


    
}
