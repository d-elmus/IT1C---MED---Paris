//service_id,monday,tuesday,wednesday,thursday,friday,saturday,sunday,start_date,end_date
public class Calendar {

    private int service_id;
    private String Enum_date;
    private java.sql.Date start_date;
    private java.sql.Date end_date;

    public Calendar(int service_id, String Enum_date,
                    java.sql.Date start_date, java.sql.Date end_date) {

        this.service_id = service_id;
        this.Enum_date = Enum_date;
        this.start_date = start_date;
        this.end_date = end_date;
    }
    @Override
    public String toString() {
        return "Calendar{" +
                "service_id='" + service_id + '\'' +
                "date = " + Enum_date + "\'" +
                ", start_date='" + start_date + '\'' +
                ", end_date='" + end_date + '\'' +
                '}';
    }
    
}
