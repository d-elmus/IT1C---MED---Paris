import java.sql.PreparedStatement;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.ResultSet;


public class GraphBuilder {

    public static Graph build(DataLoader loader) throws SQLException {

        Graph graph = new Graph();

        for (Stops stop : loader.loadStops()) {
            graph.add_vertice(new Vertice(stop));
        }

        String sql = """
        WITH station_stop_times AS (SELECT st.trip_id, st.stop_sequence, COALESCE(s.parent_station, s.stop_id) AS station_id
        FROM stop_times st JOIN stops s ON st.stop_id = s.stop_id)
        SELECT distinct station_id AS from_station,
        LEAD(station_id) OVER (PARTITION BY trip_id ORDER BY stop_sequence) AS to_station
        FROM station_stop_times;""";

        PreparedStatement ps = loader.getConnection().prepareStatement(sql);
        ResultSet rs = ps.executeQuery();

        while (rs.next()) {
            String toStation = rs.getString("to_station");

            if (toStation == null) {
                continue;
            }

            Vertice from = graph.getVertice(rs.getString("from_station"));
            Vertice to = graph.getVertice(toStation);


            if (from != null && to != null) {
                from.addEdge(new Edge(to));
            }
        }
        graph.removeEmptyVertices();
        return graph;
    }

    public static void main(String args[]){
        try {

            Connection connection = SupabaseConnector.getConnection();

            DataLoader loader = new DataLoader(connection);

            Graph graph = GraphBuilder.build(loader);

            System.out.println("Graph built successfully!\n");

            for (Vertice v : graph.getVertices()) {

                System.out.println(v.getStops().getStop_name());

                for (Edge e : v.getEdges()) {

                    System.out.println("  -> " +
                            e.getDestination().getStops().getStop_name());
                }

                System.out.println();
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
