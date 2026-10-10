import java.sql.*;
import java.util.*;

/** JDBC data-access class for projects. */
public final class ProjectDAO implements EntityDAO<Map<String,Object>> {
  public List<Map<String,Object>> findAll(Connection c)throws SQLException{return UserDAO.read(c,"SELECT payload FROM spades_projects ORDER BY created_at");}
  public Map<String,Object> findById(Connection c,String id)throws SQLException{return UserDAO.one(c,"SELECT payload FROM spades_projects WHERE id=?",id);}
  public void deleteAll(Connection c)throws SQLException{try(Statement s=c.createStatement()){s.executeUpdate("DELETE FROM spades_projects");}}
  public void replaceAll(Connection c,List<Map<String,Object>> rows)throws SQLException{String q="INSERT INTO spades_projects(id,name,owner_id,created_at,payload) VALUES(?,?,?,?,?)";try(PreparedStatement p=c.prepareStatement(q)){for(var r:rows){p.setString(1,v(r,"id"));p.setString(2,v(r,"name"));p.setString(3,v(r,"owner"));p.setString(4,v(r,"created"));p.setString(5,Main.js(r));p.addBatch();}p.executeBatch();}}
  private static String v(Map<String,Object> m,String k){return Objects.toString(m.get(k),"");}
}
