import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class Graph {
    private Map<String, Vertice> vertices = new HashMap<>();

    public Graph() {
    };

    public void add_vertice(Vertice vertice) {
        this.vertices.put(vertice.getStops().getStop_id(), vertice);
    };

    public Vertice getVertice(String stopid){
        return vertices.get(stopid);
    }

    public Collection<Vertice> getVertices() {
        return vertices.values();
    }

    public void removeEmptyVertices() {
        Iterator<Map.Entry<String, Vertice>> it = vertices.entrySet().iterator();

        while (it.hasNext()) {
            Map.Entry<String, Vertice> entry = it.next();

            if (entry.getValue().getEdges().isEmpty()) {
                it.remove();
            }
        }
    }

}
