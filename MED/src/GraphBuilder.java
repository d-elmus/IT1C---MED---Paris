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
                SELECT DISTINCT from_stop, to_stop
            FROM (
                SELECT
                    stop_id AS from_stop,
                    LEAD(stop_id) OVER (
                        PARTITION BY trip_id
                        ORDER BY stop_sequence
                    ) AS to_stop
                FROM stop_times
            ) t
            WHERE to_stop IS NOT NULL;
            """;

        PreparedStatement ps = loader.getConnection().prepareStatement(sql);
        ResultSet rs = ps.executeQuery();

        while (rs.next()) {

            String toStop = rs.getString("to_stop");

            if (toStop == null) {
                continue;
            }

            Vertice from = graph.getVertice(rs.getString("from_stop"));
            Vertice to = graph.getVertice(toStop);

            if (from != null && to != null) {
                from.addEdge(new Edge(to));
            }
        }

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

                    System.out.println("    -> " +
                            e.getDestination().getStops().getStop_name());
                }

                System.out.println();
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
