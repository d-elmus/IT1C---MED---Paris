//from_stop_id,to_stop_id,transfer_type,min_transfer_time
public class Transfers {
    private String from_stop_id;
    private String to_stop_id;
    private String transfer_type;
    private String min_transfer_time;

    public Transfers(String[] transfers) {
        this.from_stop_id = transfers[0];
        this.to_stop_id = transfers[1];
        this.transfer_type = transfers[2];
        this.min_transfer_time = transfers[3];
    }

    @Override
    public String toString() {
        return "Transfers{" +
                "from_stop_id='" + from_stop_id + '\'' +
                ", to_stop_id='" + to_stop_id + '\'' +
                ", transfer_type='" + transfer_type + '\'' +
                ", min_transfer_time='" + min_transfer_time + '\'' +
                '}';
    }

}
