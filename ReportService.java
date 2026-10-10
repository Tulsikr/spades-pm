import java.util.*;
import java.util.concurrent.*;

/** Demonstrates asynchronous report generation using a dedicated worker pool. */
public final class ReportService {
  private static final ExecutorService WORKERS=Executors.newFixedThreadPool(2,r->{Thread t=new Thread(r,"spades-report-worker");t.setDaemon(true);return t;});
  private ReportService(){}
  public static CompletableFuture<Map<String,Object>> summaryAsync(List<Map<String,Object>> projects,List<Map<String,Object>> tasks){
    return CompletableFuture.supplyAsync(()->{
      long done=tasks.stream().filter(t->"done".equals(t.get("status"))).count();
      long overdue=tasks.stream().filter(t->!"done".equals(t.get("status"))&&t.get("due")!=null&&!t.get("due").toString().isBlank()&&t.get("due").toString().compareTo(java.time.LocalDate.now().toString())<0).count();
      Map<String,Object> out=new LinkedHashMap<>();out.put("projects",projects.size());out.put("tasks",tasks.size());out.put("completedTasks",done);out.put("openTasks",tasks.size()-done);out.put("overdueTasks",overdue);out.put("completionPercent",tasks.isEmpty()?0:Math.round(done*100.0/tasks.size()));return out;
    },WORKERS);
  }
}
