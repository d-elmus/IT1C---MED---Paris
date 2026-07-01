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

    public String getFrom_stop_id() {
        return from_stop_id;
    }

    public String getTo_stop_id() {
        return to_stop_id;
    }

    public String getTransfer_type() {
        return transfer_type;
    }

    public String getMin_transfer_time() {
        return min_transfer_time;
    }
}
