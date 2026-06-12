public class Agency {
    private String agency_id;
    private String agency_name;
    private String agency_url;
    private String agency_timezone;
    private String agency_lang;
    private String agency_phone;
    private String agency_email;
    private String agency_fare_url;

    public Agency(String[] agencies) {
        this.agency_id = agencies[0];
        this.agency_name = agencies[1];
        this.agency_url = agencies[2];
        this.agency_timezone = agencies[3];
        this.agency_lang = agencies[4];
        this.agency_phone = agencies[5];
        this.agency_email = agencies[6];
        this.agency_fare_url = agencies[7];
    }


    @Override
    public String toString() {
        return "Agency{" +
                "id='" + agency_id + '\'' +
                ", name='" + agency_name + '\'' +
                ", url='" + agency_url + '\'' +
                ", timezone='" + agency_timezone + '\'' +
                ", lang='" + agency_lang + '\'' +
                ", phone='" + agency_phone + '\'' +
                ", email='" + agency_email + '\'' +
                ", fare_url='" + agency_fare_url + '\'' +
                '}';
    }
}
