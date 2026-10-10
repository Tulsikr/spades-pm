import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/** Central JDBC connection factory. Credentials are read from environment variables. */
public final class DBConnection {
  private DBConnection() {}
  public static Connection getConnection() throws SQLException {
    String url = System.getenv().getOrDefault("SPADES_DB_URL", "jdbc:mysql://localhost:3306/spades_pm?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC");
    String user = System.getenv().getOrDefault("SPADES_DB_USER", "root");
    String password = System.getenv().getOrDefault("SPADES_DB_PASSWORD", "");
    return DriverManager.getConnection(url, user, password);
  }
}
