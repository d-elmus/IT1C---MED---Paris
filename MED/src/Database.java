import java.io.FileInputStream;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

// Gere la connexion a la base Supabase 

public class Database {
    private static final Properties props = new Properties();

    static {
        // On essaie de charger db.properties depuis quelques emplacements habituels.
        for (String candidate : new String[] { "db.properties", "MED/db.properties", "../db.properties" }) {
            try (InputStream in = new FileInputStream(candidate)) {
                props.load(in);
                break;
            } catch (Exception ignored) {
                // fichier absent a cet endroit, on continue
            }
        }
    }

    // Renvoie une connexion ouverte. A fermer apres usage (try-with-resources).
    public static Connection getConnection() throws SQLException {
        String url = get("DB_URL");
        String user = get("DB_USER");
        String password = get("DB_PASSWORD");
        if (url == null || user == null || password == null) {
            throw new SQLException("Identifiants manquants : definis DB_URL, DB_USER, DB_PASSWORD "
                    + "(variables d'environnement ou fichier db.properties).");
        }
        return DriverManager.getConnection(url, user, password);
    }

    
    private static String get(String key) {
        String env = System.getenv(key);
        if (env != null && !env.isBlank()) {
            return env;
        }
        return props.getProperty(key);
    }
}
