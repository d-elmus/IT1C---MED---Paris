//service_id,monday,tuesday,wednesday,thursday,friday,saturday,sunday,start_date,end_date
public class Calendar {
    private String service_id;
    private String monday;
    private String tuesday;
    private String wednesday;
    private String thursday;
    private String friday;
    private String saturday;
    private String sunday;
    private String start_date;
    private String end_date;

    public Calendar(String[] calendar) {
        this.service_id = calendar[0];
        this.monday = calendar[1];
        this.tuesday = calendar[2];
        this.wednesday = calendar[3];
        this.thursday = calendar[4];
        this.friday = calendar[5];
        this.saturday = calendar[6];
        this.sunday = calendar[7];
        this.start_date = calendar[8];
        this.end_date = calendar[9];
    }

    @Override
    public String toString() {
        return "Calendar{" +
                "service_id='" + service_id + '\'' +
                ", monday='" + monday + '\'' +
                ", tuesday='" + tuesday + '\'' +
                ", wednesday='" + wednesday + '\'' +
                ", thursday='" + thursday + '\'' +
                ", friday='" + friday + '\'' +
                ", saturday='" + saturday + '\'' +
                ", sunday='" + sunday + '\'' +
                ", start_date='" + start_date + '\'' +
                ", end_date='" + end_date + '\'' +
                '}';
    }
    
}
