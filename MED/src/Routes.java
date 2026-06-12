//route_id,agency_id,route_short_name,route_long_name,route_desc,route_type,route_url,route_color,route_text_color,route_sort_order

public class Routes {
    private String route_id;
    private String agency_id;
    private String route_short_name;
    private String route_long_name;
    private String route_desc;
    private String route_type;
    private String route_url;
    private String route_color;
    private String route_text_color;
    private String route_sort_order;

    public Routes(String[] routes) {
        this.route_id = routes[0];
        this.agency_id = routes[1];
        this.route_short_name = routes[2];
        this.route_long_name = routes[3];
        this.route_desc = routes[4];
        this.route_type = routes[5];
        this.route_url = routes[6];
        this.route_color = routes[7];
        this.route_text_color = routes[8];
        this.route_sort_order = routes[9];
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
