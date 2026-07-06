import java.io.FileInputStream;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

// Gère la connexion à la base de données locale ou distante
public class Database {
    private static final Properties props = new Properties();

    static {
        // On essaie de charger db.properties depuis quelques emplacements habituels.
        for (String candidate : new String[] { "db.properties", "MED/db.properties", "../db.properties" }) {
            try (InputStream in = new FileInputStream(candidate)) {
                props.load(in);
                break;
            } catch (Exception ignored) {
                // fichier absent à cet endroit, on continue
            }
        }
    }

    // Renvoie une connexion ouverte. A fermer apres usage (try-with-resources).
    public static Connection getConnection() throws SQLException {
        String databaseUrl = get("DATABASE_URL");

        if (databaseUrl == null) {
            throw new SQLException("Identifiant manquant : définis DATABASE_URL "
                    + "(variable d'environnement ou dans le fichier db.properties).");
        }

        // DriverManager peut analyser l'URL complète si elle contient l'user et le password
        return DriverManager.getConnection(databaseUrl);
    }

    private static String get(String key) {
        String env = System.getenv(key);
        if (env != null && !env.isBlank()) {
            return env;
        }
        return props.getProperty(key);
    }
}
