import java.io.IOException;
import java.nio.file.*;
import java.sql.*;
import java.util.*;

/** MySQL JDBC persistence adapter. JSON remains the default so existing projects still run unchanged. */
public final class JdbcStore {
  private JdbcStore() {}
  public static boolean enabled(){return "mysql".equalsIgnoreCase(System.getenv().getOrDefault("SPADES_STORAGE","json"));}
  private static void schema(Connection c)throws SQLException{
    try(Statement s=c.createStatement()){
      s.executeUpdate("CREATE TABLE IF NOT EXISTS spades_users(id VARCHAR(32) PRIMARY KEY,email VARCHAR(190) NOT NULL UNIQUE,name VARCHAR(100) NOT NULL,role VARCHAR(20) NOT NULL,created_at VARCHAR(40),payload LONGTEXT NOT NULL) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
      s.executeUpdate("CREATE TABLE IF NOT EXISTS spades_projects(id VARCHAR(32) PRIMARY KEY,name VARCHAR(100) NOT NULL,owner_id VARCHAR(32),created_at VARCHAR(40),payload LONGTEXT NOT NULL,INDEX idx_project_owner(owner_id)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
      s.executeUpdate("CREATE TABLE IF NOT EXISTS spades_tasks(id VARCHAR(32) PRIMARY KEY,project_id VARCHAR(32) NOT NULL,title VARCHAR(160) NOT NULL,status VARCHAR(20),priority VARCHAR(20),assignee_id VARCHAR(32),due_date VARCHAR(20),created_at VARCHAR(40),payload LONGTEXT NOT NULL,INDEX idx_task_project(project_id),INDEX idx_task_assignee(assignee_id),INDEX idx_task_due(due_date)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
      s.executeUpdate("CREATE TABLE IF NOT EXISTS spades_sessions(token VARCHAR(100) PRIMARY KEY,user_id VARCHAR(32) NOT NULL,INDEX idx_session_user(user_id)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
    }
  }
  @SuppressWarnings("unchecked") public static Map<String,Object> loadOrMigrate(Path jsonPath)throws Exception{
    try(Connection c=DBConnection.getConnection()){schema(c);long count;try(Statement s=c.createStatement();ResultSet r=s.executeQuery("SELECT COUNT(*) FROM spades_users")){r.next();count=r.getLong(1);}
      if(count==0 && Files.exists(jsonPath)){
        Map<String,Object> legacy=(Map<String,Object>)new Main.P(Files.readString(jsonPath)).v(); defaults(legacy); saveState(c,legacy); System.out.println("Imported existing data.json into MySQL."); return legacy;
      }
      Map<String,Object> state=new LinkedHashMap<>();state.put("users",new UserDAO().findAll(c));state.put("projects",new ProjectDAO().findAll(c));state.put("tasks",new TaskDAO().findAll(c));Map<String,Object> sessions=new LinkedHashMap<>();
      try(Statement s=c.createStatement();ResultSet r=s.executeQuery("SELECT token,user_id FROM spades_sessions")){while(r.next())sessions.put(r.getString(1),r.getString(2));}state.put("sessions",sessions);defaults(state);return state;
    }
  }
  public static void saveState(Map<String,Object> state)throws SQLException{try(Connection c=DBConnection.getConnection()){schema(c);saveState(c,state);}}
  @SuppressWarnings("unchecked") private static void saveState(Connection c,Map<String,Object> state)throws SQLException{
    boolean auto=c.getAutoCommit();c.setAutoCommit(false);try{
      // Delete children first; each complete snapshot is written atomically inside one transaction.
      new TaskDAO().deleteAll(c);new ProjectDAO().deleteAll(c);new UserDAO().deleteAll(c);
      new UserDAO().replaceAll(c,(List<Map<String,Object>>)state.get("users"));
      new ProjectDAO().replaceAll(c,(List<Map<String,Object>>)state.get("projects"));
      new TaskDAO().replaceAll(c,(List<Map<String,Object>>)state.get("tasks"));
      try(Statement s=c.createStatement()){s.executeUpdate("DELETE FROM spades_sessions");}
      Map<String,Object> sessions=(Map<String,Object>)state.getOrDefault("sessions",new LinkedHashMap<>());
      try(PreparedStatement p=c.prepareStatement("INSERT INTO spades_sessions(token,user_id) VALUES(?,?)")){for(var e:sessions.entrySet()){p.setString(1,e.getKey());p.setString(2,Objects.toString(e.getValue(),""));p.addBatch();}p.executeBatch();}
      c.commit();
    }catch(Exception e){c.rollback();if(e instanceof SQLException se)throw se;throw new SQLException("Could not persist workspace",e);}finally{c.setAutoCommit(auto);}
  }
  private static void defaults(Map<String,Object> s){s.putIfAbsent("users",new ArrayList<>());s.putIfAbsent("projects",new ArrayList<>());s.putIfAbsent("tasks",new ArrayList<>());s.putIfAbsent("sessions",new LinkedHashMap<>());}
}
