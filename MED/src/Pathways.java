//pathway_id,from_stop_id,to_stop_id,pathway_mode,is_bidirectional,length,traversal_time,stair_count,max_slope,min_width,signposted_as,reversed_signposted_as
public class Pathways {
    private String pathway_id;
    private String from_stop_id;
    private String to_stop_id;
    private String pathway_mode;
    private String is_bidirectional;
    private String length;
    private String traversal_time;
    private String stair_count;
    private String max_slope;
    private String min_width;
    private String signposted_as;
    private String reversed_signposted_as;

    public Pathways(String[] pathways) {
        this.pathway_id = pathways[0];
        this.from_stop_id = pathways[1];
        this.to_stop_id = pathways[2];
        this.pathway_mode = pathways[3];
        this.is_bidirectional = pathways[4];
        this.length = pathways[5];
        this.traversal_time = pathways[6];
        this.stair_count = pathways[7];
        this.max_slope = pathways[8];
        this.min_width = pathways[9];
        this.signposted_as = pathways[10];
        this.reversed_signposted_as = pathways[11];
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
