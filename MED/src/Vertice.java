import java.util.ArrayList;
import java.util.List;

public class Vertice {
    private Stops stops;
    private List<Edge> edges = new ArrayList<>();

    public Vertice(Stops stops){
        this.stops = stops;
    };
    public Vertice(){};

    public Stops getStops(){
        return stops;
    }

    public List<Edge> getEdges() {
        return edges;
    }
    public void addEdge(Edge edge){
        this.edges.add(edge);
    }
}
