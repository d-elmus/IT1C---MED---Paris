public class Agency {
    private String agency_id;
    private String agency_name;
    private String agency_url;
    private String agency_timezone;
    private String agency_lang;
    private String agency_phone;
    private String agency_email;
    private String agency_fare_url;

    public Agency(String agency_id, String agency_name, String agency_url,
                  String agency_timezone, String agency_lang,
                  String agency_phone, String agency_email) {

        this.agency_id = agency_id;
        this.agency_name = agency_name;
        this.agency_url = agency_url;
        this.agency_timezone = agency_timezone;
        this.agency_lang = agency_lang;
        this.agency_phone = agency_phone;
        this.agency_email = agency_email;
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
