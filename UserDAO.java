import java.sql.*;
import java.util.*;

/** JDBC data-access class for user accounts. Password hashes are stored, never raw passwords. */
public final class UserDAO implements EntityDAO<Map<String,Object>> {
  public List<Map<String,Object>> findAll(Connection c) throws SQLException { return read(c, "SELECT payload FROM spades_users ORDER BY created_at"); }
  public Map<String,Object> findById(Connection c, String id) throws SQLException { return one(c,"SELECT payload FROM spades_users WHERE id=?",id); }
  public void deleteAll(Connection c) throws SQLException { try(Statement s=c.createStatement()){s.executeUpdate("DELETE FROM spades_users");} }
  public void replaceAll(Connection c,List<Map<String,Object>> rows)throws SQLException {
    String q="INSERT INTO spades_users(id,email,name,role,created_at,payload) VALUES(?,?,?,?,?,?)";
    try(PreparedStatement p=c.prepareStatement(q)){ for(var r:rows){p.setString(1,val(r,"id"));p.setString(2,val(r,"email"));p.setString(3,val(r,"name"));p.setString(4,val(r,"role"));p.setString(5,val(r,"created"));p.setString(6,Main.js(r));p.addBatch();} p.executeBatch(); }
  }
  private static String val(Map<String,Object> m,String k){return Objects.toString(m.get(k),"");}
  static List<Map<String,Object>> read(Connection c,String q)throws SQLException{List<Map<String,Object>> a=new ArrayList<>();try(Statement s=c.createStatement();ResultSet r=s.executeQuery(q)){while(r.next())a.add(parse(r.getString(1)));}return a;}
  static Map<String,Object> one(Connection c,String q,String id)throws SQLException{try(PreparedStatement p=c.prepareStatement(q)){p.setString(1,id);try(ResultSet r=p.executeQuery()){return r.next()?parse(r.getString(1)):null;}}}
  @SuppressWarnings("unchecked") static Map<String,Object> parse(String json)throws SQLException{try{return (Map<String,Object>)new Main.P(json).v();}catch(Exception e){throw new SQLException("Invalid JSON payload in database",e);}}
}
