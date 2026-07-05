//stop_id,stop_code,stop_name,stop_desc,stop_lon,stop_lat,zone_id,stop_url,location_type,parent_station,stop_timezone,level_id,wheelchair_boarding,platform_code
public class Stops {
    private String stop_id;
    private int level_id;
    private String zone_id;
    private String stop_code;
    private String stop_name;
    private String stop_desc;
    private String stop_lat;
    private String stop_lon;
    private String plateform_code;
    private String stop_url;
    private String location_type;
    private String parent_station;
    private int wheelchair_boarding;
    private String stop_timezone;


    public Stops(String[] row) {
        this.stop_id             = row[0];
        this.stop_code           = row[1];
        this.stop_name           = row[2];
        this.stop_desc           = row[3];
        this.stop_lon            = row[4];
        this.stop_lat            = row[5];
        this.zone_id             = row[6];
        this.stop_url            = row[7];
        this.location_type       = row[8];
        this.parent_station      = row[9];
        this.stop_timezone       = row[10];
        this.level_id            = row[11] != null ? Integer.parseInt(row[11]) : 0;
        this.wheelchair_boarding = row[12] != null ? Integer.parseInt(row[12]) : 0;
        this.plateform_code      = row[13];
    }

    public Stops(String stop_id, int level_id, String zone_id,
                 String stop_code, String stop_name, String stop_desc,
                 String stop_lat, String stop_lon, String plateform_code,
                 String stop_url, String location_type, String parent_station,
                 int wheelchair_boarding, String stop_timezone) {

        this.stop_id = stop_id;
        this.level_id = level_id;
        this.zone_id = zone_id;
        this.stop_code = stop_code;
        this.stop_name = stop_name;
        this.stop_desc = stop_desc;
        this.stop_lat = stop_lat;
        this.stop_lon = stop_lon;
        this.plateform_code = plateform_code;
        this.stop_url = stop_url;
        this.location_type = location_type;
        this.parent_station = parent_station;
        this.wheelchair_boarding = wheelchair_boarding;
        this.stop_timezone = stop_timezone;
    }


    @Override
    public String toString() {
        return "Stops{" +
                "stop_id='" + stop_id + '\'' +
                ", stop_code='" + stop_code + '\'' +
                ", stop_name='" + stop_name + '\'' +
                ", stop_desc='" + stop_desc + '\'' +
                ", stop_lon='" + stop_lon + '\'' +
                ", stop_lat='" + stop_lat + '\'' +
                ", zone_id='" + zone_id + '\'' +
                ", stop_url='" + stop_url + '\'' +
                ", location_type='" + location_type + '\'' +
                ", parent_station='" + parent_station + '\'' +
                ", stop_timezone='" + stop_timezone + '\'' +
                ", level_id='" + level_id + '\'' +
                ", wheelchair_boarding='" + wheelchair_boarding + '\'' +
                ", platform_code='" + plateform_code + '\'' +
                '}';
    }

    public String getStop_id() {
        return stop_id;
    }

    public String getStop_lat() {
        return stop_lat;
    }

    public String getStop_lon() {
        return stop_lon;
    }

    public String getParent_station(){ return this.parent_station;}

    public String getStop_name() {
        return this.stop_name;
    }
}

