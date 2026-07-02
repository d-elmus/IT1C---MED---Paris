//service_id,date,exception_type
import java.sql.Date;

public class Calendar_dates {

    private int service_id;
    private Date date;
    private int exception_type;

    public Calendar_dates(int service_id, Date date,
                         int exception_type) {

        this.service_id = service_id;
        this.date = date;
        this.exception_type = exception_type;
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
