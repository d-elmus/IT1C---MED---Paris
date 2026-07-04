//object_id,object_system,object_code
public class Stops_extension {

    private String object_id;
    private String stop_id;
    private String object_system;
    private String object_code;

    public Stops_extension(String object_id, String stop_id,
                          String object_system, String object_code) {

        this.object_id = object_id;
        this.stop_id = stop_id;
        this.object_system = object_system;
        this.object_code = object_code;
    }

    @Override
    public String toString() {
        return "Stops_extension{" +
                "object_id='" + object_id + '\'' +
                ", object_system='" + object_system + '\'' +
                ", object_code='" + object_code + '\'' +
                '}';
    }  
}
