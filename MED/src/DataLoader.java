import java.sql.PreparedStatement;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.ResultSet;
import java.util.List;
import java.util.ArrayList;


public class DataLoader {

    private Connection connection;

    public DataLoader(Connection connection) {
        this.connection = connection;
    };

    public List<Agency> loadAgency() throws SQLException {

        List<Agency> agencies = new ArrayList<>();

        String sql = "SELECT * FROM agency";

        PreparedStatement statement = connection.prepareStatement(sql);
        ResultSet rs = statement.executeQuery();

        while (rs.next()) {
            agencies.add(new Agency(
                    rs.getString("agency_id"),
                    rs.getString("agency_name"),
                    rs.getString("agency_url"),
                    rs.getString("agency_timezone"),
                    rs.getString("agency_lang"),
                    rs.getString("agency_phone"),
                    rs.getString("agency_email")
            ));
        }

        return agencies;
    }

    public List<Routes> loadRoutes() throws SQLException {

        List<Routes> routes = new ArrayList<>();

        String sql = "SELECT * FROM routes";

        PreparedStatement statement = connection.prepareStatement(sql);
        ResultSet rs = statement.executeQuery();

        while (rs.next()) {
            routes.add(new Routes(
                    rs.getString("route_id"),
                    rs.getInt("agency_id"),
                    rs.getString("route_short_name"),
                    rs.getString("route_long_name"),
                    rs.getString("route_desc"),
                    rs.getInt("route_type"),
                    rs.getString("route_url"),
                    rs.getString("route_color"),
                    rs.getString("route_text_color"),
                    rs.getInt("route_sort_order")
            ));
        }

        return routes;
    }

    public List<Trips> loadTrips() throws SQLException {

        List<Trips> trips = new ArrayList<>();

        String sql = "SELECT * FROM trips";

        PreparedStatement statement = connection.prepareStatement(sql);
        ResultSet rs = statement.executeQuery();

        while (rs.next()) {
            trips.add(new Trips(
                    rs.getInt("trip_id"),
                    rs.getString("route_id"),
                    rs.getInt("service_id"),
                    rs.getBoolean("direction_id"),
                    rs.getInt("block_id"),
                    rs.getInt("shape_id"),
                    rs.getString("trip_short_name"),
                    rs.getString("trip_headsign"),
                    rs.getBoolean("wheelchair_accessible"),
                    rs.getBoolean("bike_allowed")
            ));
        }

        return trips;
    }

    public List<Stops> loadStops() throws SQLException {

        List<Stops> stops = new ArrayList<>();

        String sql = "SELECT * FROM stops";

        PreparedStatement statement = connection.prepareStatement(sql);
        ResultSet rs = statement.executeQuery();

        while (rs.next()) {
            stops.add(new Stops(
                    rs.getString("stop_id"),
                    rs.getInt("level_id"),
                    rs.getString("zone_id"),
                    rs.getString("stop_code"),
                    rs.getString("stop_name"),
                    rs.getString("stop_desc"),
                    rs.getString("stop_lat"),
                    rs.getString("stop_lon"),
                    rs.getString("platform_code"),
                    rs.getString("stop_url"),
                    rs.getString("location_type"),
                    rs.getString("parent_station"),
                    rs.getInt("wheelchair_boarding"),
                    rs.getString("stop_timezone")
            ));
        }

        return stops;
    }

    public List<Stops_times> loadStopTimes() throws SQLException {

        List<Stops_times> stopTimes = new ArrayList<>();

        String sql = "SELECT * FROM stop_times";

        PreparedStatement statement = connection.prepareStatement(sql);
        ResultSet rs = statement.executeQuery();

        while (rs.next()) {
            stopTimes.add(new Stops_times(
                    rs.getString("trip_id"),
                    rs.getInt("stop_sequence"),
                    rs.getString("arrival_time"),
                    rs.getString("stop_id"),
                    rs.getString("departure_time"),
                    rs.getBoolean("pickup_type"),
                    rs.getBoolean("drop_off_type"),
                    rs.getInt("local_zone_id"),
                    rs.getString("stop_headsign"),
                    rs.getBoolean("timepoint")
            ));
        }

        return stopTimes;
    }

    public List<Transfers> loadTransfers() throws SQLException {

        List<Transfers> transfers = new ArrayList<>();

        String sql = "SELECT * FROM transfers";

        PreparedStatement statement = connection.prepareStatement(sql);
        ResultSet rs = statement.executeQuery();

        while (rs.next()) {
            transfers.add(new Transfers(
                    rs.getString("from_stop_id"),
                    rs.getString("to_stop_id"),
                    rs.getString("transfer_type"),
                    rs.getString("min_transfer_time")
            ));
        }

        return transfers;
    }

    public List<Pathways> loadPathways() throws SQLException {

        List<Pathways> pathways = new ArrayList<>();

        String sql = "SELECT * FROM pathways";

        PreparedStatement statement = connection.prepareStatement(sql);
        ResultSet rs = statement.executeQuery();

        while (rs.next()) {
            pathways.add(new Pathways(
                    rs.getString("pathways_id"),
                    rs.getString("from_stop_id"),
                    rs.getString("to_stop_id"),
                    rs.getInt("pathway_mode"),
                    rs.getBoolean("is_bidirectional"),
                    rs.getDouble("length"),
                    rs.getInt("traversal_time"),
                    rs.getInt("stair_count"),
                    rs.getInt("max_slope"),
                    rs.getInt("min_width"),
                    rs.getString("signposted_as"),
                    rs.getString("reversed_signposted_as")
            ));
        }

        return pathways;
    }

    public List<Calendar> loadCalendar() throws SQLException {

        List<Calendar> calendars = new ArrayList<>();

        String sql = "SELECT * FROM calendar";

        PreparedStatement statement = connection.prepareStatement(sql);
        ResultSet rs = statement.executeQuery();

        while (rs.next()) {
            calendars.add(new Calendar(
                    rs.getInt("service_id"),
                    rs.getString("Enum_date"),
                    rs.getDate("start_date"),
                    rs.getDate("end_date")
            ));
        }

        return calendars;
    }

    public List<Calendar_dates> loadCalendarDates() throws SQLException {

        List<Calendar_dates> calendarDates = new ArrayList<>();

        String sql = "SELECT * FROM calendar_dates";

        PreparedStatement statement = connection.prepareStatement(sql);
        ResultSet rs = statement.executeQuery();

        while (rs.next()) {
            calendarDates.add(new Calendar_dates(
                    rs.getInt("service_id"),
                    rs.getDate("dates"),
                    rs.getInt("exception_type")
            ));
        }

        return calendarDates;
    }

    public List<Stops_extension> loadStopExtensions() throws SQLException {

        List<Stops_extension> stopExtensions = new ArrayList<>();

        String sql = "SELECT * FROM stop_extensions";

        PreparedStatement statement = connection.prepareStatement(sql);
        ResultSet rs = statement.executeQuery();

        while (rs.next()) {
            stopExtensions.add(new Stops_extension(
                    rs.getString("object_id"),
                    rs.getString("stop_id"),
                    rs.getString("object_system"),
                    rs.getString("object_code")
            ));
        }

        return stopExtensions;
    }

    public Connection getConnection() {
        return connection;
    }
}
