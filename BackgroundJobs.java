import java.util.concurrent.*;

/** Scheduled background worker; reminders can be extended without blocking the web request threads. */
public final class BackgroundJobs {
  private static final ScheduledExecutorService SCHEDULER=Executors.newSingleThreadScheduledExecutor(r->{Thread t=new Thread(r,"spades-scheduled-jobs");t.setDaemon(true);return t;});
  private BackgroundJobs(){}
  public static void start(){SCHEDULER.scheduleAtFixedRate(()->{
    try { synchronized(Main.class){ if(Main.db!=null){ long overdue=Main.tbl("tasks").stream().filter(t->!"done".equals(t.get("status"))&&t.get("due")!=null&&!t.get("due").toString().isBlank()&&t.get("due").toString().compareTo(java.time.LocalDate.now().toString())<0).count(); if(overdue>0) System.out.println("[Spades PM reminder worker] "+overdue+" overdue task(s) need attention."); } } }
    catch(Exception e){System.err.println("Background reminder check failed: "+e.getMessage());}
  },1,60,TimeUnit.SECONDS);}
}
