//stop_id,stop_code,stop_name,stop_desc,stop_lon,stop_lat,zone_id,stop_url,location_type,parent_station,stop_timezone,level_id,wheelchair_boarding,platform_code
public class Stops {
    private String stop_id;
    private String stop_code;
    private String stop_name;
    private String stop_desc;
    private String stop_lon;
    private String stop_lat;
    private String zone_id;
    private String stop_url;
    private String location_type;
    private String parent_station;
    private String stop_timezone;
    private String level_id;
    private String wheelchair_boarding;
    private String platform_code;

    public Stops(String[] stops) {
        this.stop_id = stops[0];
        this.stop_code = stops[1];
        this.stop_name = stops[2];
        this.stop_desc = stops[3];
        this.stop_lon = stops[4];
        this.stop_lat = stops[5];
        this.zone_id = stops[6];
        this.stop_url = stops[7];
        this.location_type = stops[8];
        this.parent_station = stops[9];
        this.stop_timezone = stops[10];
        this.level_id = stops[11];
        this.wheelchair_boarding = stops[12];
        this.platform_code = stops[13];
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
                ", platform_code='" + platform_code + '\'' +
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
   
}
