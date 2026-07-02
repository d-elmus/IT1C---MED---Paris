public class Edge {
    private Vertice toVertice;
    private String color;

    public Edge( Vertice toVertice, String color){
;
        this.toVertice = toVertice;
        this.color = color;
    }
    public Edge(Vertice toVertice){
        this.toVertice = toVertice;
    };

    public Vertice getDestination(){
        return toVertice;
    }

    public String getColor() {
        return color;
    }
}
