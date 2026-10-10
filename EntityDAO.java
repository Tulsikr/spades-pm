import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

/** Generic DAO contract used by the MySQL persistence layer. */
public interface EntityDAO<T> {
  List<Map<String,Object>> findAll(Connection connection) throws SQLException;
  void replaceAll(Connection connection, List<Map<String,Object>> records) throws SQLException;
  T findById(Connection connection, String id) throws SQLException;
  void deleteAll(Connection connection) throws SQLException;
}
