import java.sql.*;
import java.util.*;

/** JDBC data-access class for tasks, including comments retained in the JSON payload. */
public final class TaskDAO implements EntityDAO<Map<String,Object>> {
  public List<Map<String,Object>> findAll(Connection c)throws SQLException{return UserDAO.read(c,"SELECT payload FROM spades_tasks ORDER BY created_at");}
  public Map<String,Object> findById(Connection c,String id)throws SQLException{return UserDAO.one(c,"SELECT payload FROM spades_tasks WHERE id=?",id);}
  public void deleteAll(Connection c)throws SQLException{try(Statement s=c.createStatement()){s.executeUpdate("DELETE FROM spades_tasks");}}
  public void replaceAll(Connection c,List<Map<String,Object>> rows)throws SQLException{String q="INSERT INTO spades_tasks(id,project_id,title,status,priority,assignee_id,due_date,created_at,payload) VALUES(?,?,?,?,?,?,?,?,?)";try(PreparedStatement p=c.prepareStatement(q)){for(var r:rows){p.setString(1,v(r,"id"));p.setString(2,v(r,"project"));p.setString(3,v(r,"title"));p.setString(4,v(r,"status"));p.setString(5,v(r,"priority"));p.setString(6,v(r,"assignee"));p.setString(7,v(r,"due"));p.setString(8,v(r,"created"));p.setString(9,Main.js(r));p.addBatch();}p.executeBatch();}}
  private static String v(Map<String,Object> m,String k){return Objects.toString(m.get(k),"");}
}
