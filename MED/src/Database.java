import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class Database {

    // Renvoie une connexion ouverte. A fermer apres usage (try-with-resources).
    public static Connection getConnection() throws SQLException {
        String url = "jdbc:postgresql://aws-0-eu-west-1.pooler.supabase.com:5432/postgres"
                   + "?user=postgres.lqxsqdkfyxfcxmgzilld&password=Xm2uhZtOobmCDp3m";
        return DriverManager.getConnection(url);
    }
}
