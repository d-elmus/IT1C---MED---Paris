import com.sun.net.httpserver.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.List;

public class MetroServer {

    private static final int PORT = 8080;
    private static List<MetroLoader.StationInfo> stations;

    public static void start(List<MetroLoader.StationInfo> stationList) throws IOException {
        stations = stationList;
        HttpServer server = HttpServer.create(new InetSocketAddress(PORT), 0);
        server.createContext("/api/stations", MetroServer::handleStations);
        server.createContext("/",             MetroServer::handleStatic);
        server.start();
        System.out.println("Serveur démarré → http://localhost:" + PORT);
    }

    // ---------------------------------------------------------------
    // GET /api/stations  → JSON [{name, lat, lon}, …]

    private static void handleStations(HttpExchange ex) throws IOException {
        if (!"GET".equalsIgnoreCase(ex.getRequestMethod())) {
            ex.sendResponseHeaders(405, -1);
            return;
        }
        StringBuilder sb = new StringBuilder("[\n");
        for (int i = 0; i < stations.size(); i++) {
            MetroLoader.StationInfo s = stations.get(i);
            sb.append("  {\"name\":\"")
              .append(s.name.replace("\\", "\\\\").replace("\"", "\\\""))
              .append("\",\"lat\":").append(s.lat)
              .append(",\"lon\":").append(s.lon)
              .append("}");
            if (i < stations.size() - 1) sb.append(",");
            sb.append("\n");
        }
        sb.append("]");
        byte[] body = sb.toString().getBytes(StandardCharsets.UTF_8);
        Headers h = ex.getResponseHeaders();
        h.set("Content-Type", "application/json; charset=utf-8");
        h.set("Access-Control-Allow-Origin", "*");
        ex.sendResponseHeaders(200, body.length);
        try (OutputStream os = ex.getResponseBody()) { os.write(body); }
    }

    // ---------------------------------------------------------------
    // GET /  → sert web/index.html et les autres fichiers statiques

    private static void handleStatic(HttpExchange ex) throws IOException {
        String uriPath = ex.getRequestURI().getPath();
        if ("/".equals(uriPath) || uriPath.isEmpty()) uriPath = "/index.html";

        // Protection path-traversal
        Path webRoot  = Path.of("web").toAbsolutePath().normalize();
        String rel    = uriPath.startsWith("/") ? uriPath.substring(1) : uriPath;
        Path filePath = webRoot.resolve(rel).normalize();
        if (!filePath.startsWith(webRoot)) {
            ex.sendResponseHeaders(403, -1);
            return;
        }

        if (!Files.exists(filePath) || Files.isDirectory(filePath)) {
            byte[] msg = "404 Not Found".getBytes(StandardCharsets.UTF_8);
            ex.sendResponseHeaders(404, msg.length);
            try (OutputStream os = ex.getResponseBody()) { os.write(msg); }
            return;
        }

        byte[] bytes = Files.readAllBytes(filePath);
        ex.getResponseHeaders().set("Content-Type", contentType(uriPath));
        ex.sendResponseHeaders(200, bytes.length);
        try (OutputStream os = ex.getResponseBody()) { os.write(bytes); }
    }

    private static String contentType(String path) {
        if (path.endsWith(".html")) return "text/html; charset=utf-8";
        if (path.endsWith(".css"))  return "text/css; charset=utf-8";
        if (path.endsWith(".js"))   return "application/javascript; charset=utf-8";
        if (path.endsWith(".json")) return "application/json; charset=utf-8";
        return "application/octet-stream";
    }
}
