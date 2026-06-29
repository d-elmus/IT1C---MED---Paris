import java.sql.PreparedStatement;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.ResultSet;

public class SupabaseConnector {
    private static final String DATABASE_URL = System.getenv("DATABASE_URL");

    public static Connection getConnection() throws SQLException {
        // 1. Correction de "DTABASE_URL" -> "DATABASE_URL"
        if (DATABASE_URL == null) {
            throw new IllegalStateException("Erreur : Variables d'environnement manquantes !");
        }
        System.out.println("DEBUG - URL testée : " + DATABASE_URL);
        // 2. Correction de "URL" -> "DATABASE_URL"
        return DriverManager.getConnection(DATABASE_URL);
    }

    public static void main(String[] args) {
        String selectSQL = "SELECT agency_id, agency_name FROM agency"; // C'est plus propre de cibler les colonnes

        try (Connection conn = getConnection()) {
            System.out.println("✅ Connexion établie avec succès à Supabase !");

            try (PreparedStatement pstmt = conn.prepareStatement(selectSQL);
                 ResultSet rs = pstmt.executeQuery()) {

                System.out.println("\n--- Liste des agences ---");

                while (rs.next()) {
                    // Utilisation de %s pour injecter dynamiquement les chaînes de caractères
                    // \n permet de passer à la ligne suivante pour chaque agence
                    System.out.printf("ID: %s | Nom: %s\n",
                            rs.getString("agency_id"),
                            rs.getString("agency_name"));
                }
            }
        } catch (SQLException e) {
            System.err.println("❌ Erreur SQL : " + e.getMessage());
        }
    }
}
