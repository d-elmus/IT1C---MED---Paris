//service_id,date,exception_type
public class Calendar_dates {
    private String service_id;
    private String date;
    private String exception_type;

    public Calendar_dates(String[] calendar_dates) {
        this.service_id = calendar_dates[0];
        this.date = calendar_dates[1];
        this.exception_type = calendar_dates[2];
    }

    @Override
    public String toString() {
        return "Calendar_dates{" +
                "service_id='" + service_id + '\'' +
                ", date='" + date + '\'' +
                ", exception_type='" + exception_type + '\'' +
                '}';
    }

    
}
