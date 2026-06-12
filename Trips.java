//route_id,service_id,trip_id,trip_headsign,trip_short_name,direction_id,block_id,shape_id,wheelchair_accessible,bikes_allowed
public class Trips {
    private String route_id;
    private String service_id;
    private String trip_id;
    private String trip_headsign;
    private String trip_short_name;
    private String direction_id;
    private String block_id;
    private String shape_id;
    private String wheelchair_accessible;
    private String bikes_allowed;

    public Trips(String[] trips) {
        this.route_id = trips[0];
        this.service_id = trips[1];
        this.trip_id = trips[2];
        this.trip_headsign = trips[3];
        this.trip_short_name = trips[4];
        this.direction_id = trips[5];
        this.block_id = trips[6];
        this.shape_id = trips[7];
        this.wheelchair_accessible = trips[8];
        this.bikes_allowed = trips[9];
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
