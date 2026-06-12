//object_id,object_system,object_code
public class Stops_extension {
    private String object_id;
    private String object_system;
    private String object_code;

    public Stops_extension(String[] stops_extension) {
        this.object_id = stops_extension[0];
        this.object_system = stops_extension[1];
        this.object_code = stops_extension[2];
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
