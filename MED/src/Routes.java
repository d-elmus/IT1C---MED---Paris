//route_id,agency_id,route_short_name,route_long_name,route_desc,route_type,route_url,route_color,route_text_color,route_sort_order

public class Routes {

    private String route_id;
    private int agency_id;
    private String route_short_name;
    private String route_long_name;
    private String route_desc;
    private int route_type;
    private String route_url;
    private String route_color;
    private String route_text_color;
    private int route_sort_order;

    public Routes(String route_id, int agency_id, String route_short_name,
                  String route_long_name, String route_desc, int route_type,
                  String route_url, String route_color,
                  String route_text_color, int route_sort_order) {

        this.route_id = route_id;
        this.agency_id = agency_id;
        this.route_short_name = route_short_name;
        this.route_long_name = route_long_name;
        this.route_desc = route_desc;
        this.route_type = route_type;
        this.route_url = route_url;
        this.route_color = route_color;
        this.route_text_color = route_text_color;
        this.route_sort_order = route_sort_order;
    }

    @Override
    public String toString() {
        return "Routes{" +
                "route_id='" + route_id + '\'' +
                ", agency_id='" + agency_id + '\'' +
                ", route_short_name='" + route_short_name + '\'' +
                ", route_long_name='" + route_long_name + '\'' +
                ", route_desc='" + route_desc + '\'' +
                ", route_type='" + route_type + '\'' +
                ", route_url='" + route_url + '\'' +
                ", route_color='" + route_color + '\'' +
                ", route_text_color='" + route_text_color + '\'' +
                ", route_sort_order='" + route_sort_order + '\'' +
                '}';
    }
    
}
