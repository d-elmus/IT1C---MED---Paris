//pathway_id,from_stop_id,to_stop_id,pathway_mode,is_bidirectional,length,traversal_time,stair_count,max_slope,min_width,signposted_as,reversed_signposted_as
public class Pathways {

    private String pathway_id;
    private String from_stop_id;
    private String to_stop_id;
    private int pathway_mode;
    private boolean is_bidirectional;
    private double length;
    private int traversal_time;
    private int stair_count;
    private int max_slope;
    private int min_width;
    private String signposted_as;
    private String reversed_signposted_as;

    public Pathways(String pathway_id, String from_stop_id, String to_stop_id,
                    int pathway_mode, boolean is_bidirectional, double length,
                    int traversal_time, int stair_count, int max_slope,
                    int min_width, String signposted_as,
                    String reversed_signposted_as) {

        this.pathway_id = pathway_id;
        this.from_stop_id = from_stop_id;
        this.to_stop_id = to_stop_id;
        this.pathway_mode = pathway_mode;
        this.is_bidirectional = is_bidirectional;
        this.length = length;
        this.traversal_time = traversal_time;
        this.stair_count = stair_count;
        this.max_slope = max_slope;
        this.min_width = min_width;
        this.signposted_as = signposted_as;
        this.reversed_signposted_as = reversed_signposted_as;
    }


    @Override
    public String toString() {
        return "Pathways{" +
                "pathway_id='" + pathway_id + '\'' +
                ", from_stop_id='" + from_stop_id + '\'' +
                ", to_stop_id='" + to_stop_id + '\'' +
                ", pathway_mode='" + pathway_mode + '\'' +
                ", is_bidirectional='" + is_bidirectional + '\'' +
                ", length='" + length + '\'' +
                ", traversal_time='" + traversal_time + '\'' +
                ", stair_count='" + stair_count + '\'' +
                ", max_slope='" + max_slope + '\'' +
                ", min_width='" + min_width + '\'' +
                ", signposted_as='" + signposted_as + '\'' +
                ", reversed_signposted_as='" + reversed_signposted_as + '\'' +
                '}';
    }
    public String get_pathway_id(){
        return from_stop_id;
    }
    
    


}
