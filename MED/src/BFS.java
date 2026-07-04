import java.sql.Connection;
import java.util.*;

public class BFS {

    public static boolean isConnected(Graph graph) {

        Collection<Vertice> vertices = graph.getVertices();

        if (vertices.isEmpty()) {
            return true;
        }

        Vertice start = vertices.iterator().next();

        Queue<Vertice> queue = new LinkedList<>();
        Set<Vertice> visited = new HashSet<>();

        queue.add(start);
        visited.add(start);

        while (!queue.isEmpty()) {

            Vertice current = queue.poll();

            for (Edge edge : current.getEdges()) {

                Vertice neighbour = edge.getDestination();

                if (!visited.contains(neighbour)) {
                    visited.add(neighbour);
                    queue.add(neighbour);
                }
            }
        }
        System.out.println(visited.size() -1 + " <- False size  " + "   True size ->" +  vertices.size());
        return visited.size() - 1  == vertices.size();

    }

    public static void main(String args[]){
        try {

                Connection connection = SupabaseConnector.getConnection();

                DataLoader loader = new DataLoader(connection);

                Graph graph = GraphBuilder.build(loader);

                System.out.println("Graph built successfully!\n");

                boolean connected = BFS.isConnected(graph);

                if(connected){
                    System.out.println("Connected");
                }
                else {
                    System.out.println("Not connected");
                }



            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }