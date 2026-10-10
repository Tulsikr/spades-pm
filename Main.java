// Spades PM - project management backend. Built by Team Spades.
// Pure JDK 17+ (no dependencies). Run: javac Main.java && java Main
import com.sun.net.httpserver.*;
import java.io.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.*;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

public class Main {
  static final Path DB = Path.of(System.getenv().getOrDefault("SPADES_DB", "data.json"));
  static final SecureRandom RND = new SecureRandom();
  static final Set<String> STATUS = Set.of("todo", "doing", "review", "done"), PRIO = Set.of("low", "medium", "high");
  static Map<String, Object> db;

  static class Err extends RuntimeException { int c; Err(int c, String m) { super(m); this.c = c; } }

  // ---------- JSON ----------
  static class P {
    String s; int i;
    P(String s) { this.s = s; }
    Object v() {
      ws(); char c = s.charAt(i);
      if (c == '{') { i++; var m = new LinkedHashMap<String, Object>(); ws();
        if (s.charAt(i) == '}') { i++; return m; }
        do { ws(); String k = str(); ws(); i++; m.put(k, v()); ws(); } while (s.charAt(i++) == ',');
        return m; }
      if (c == '[') { i++; var l = new ArrayList<Object>(); ws();
        if (s.charAt(i) == ']') { i++; return l; }
        do { l.add(v()); ws(); } while (s.charAt(i++) == ',');
        return l; }
      if (c == '"') return str();
      if (s.startsWith("true", i)) { i += 4; return true; }
      if (s.startsWith("false", i)) { i += 5; return false; }
      if (s.startsWith("null", i)) { i += 4; return null; }
      int j = i; while (i < s.length() && "+-.eE0123456789".indexOf(s.charAt(i)) >= 0) i++;
      return Double.parseDouble(s.substring(j, i));
    }
    String str() {
      i++; var b = new StringBuilder();
      while (s.charAt(i) != '"') { char c = s.charAt(i++);
        if (c == '\\') { c = s.charAt(i++);
          switch (c) { case 'n' -> b.append('\n'); case 't' -> b.append('\t'); case 'r' -> b.append('\r');
            case 'u' -> { b.append((char) Integer.parseInt(s.substring(i, i + 4), 16)); i += 4; }
            default -> b.append(c); }
        } else b.append(c); }
      i++; return b.toString();
    }
    void ws() { while (i < s.length() && Character.isWhitespace(s.charAt(i))) i++; }
  }

  static String js(Object o) {
    if (o == null) return "null";
    if (o instanceof String s) {
      var b = new StringBuilder("\"");
      for (char c : s.toCharArray()) switch (c) {
        case '"' -> b.append("\\\""); case '\\' -> b.append("\\\\"); case '\n' -> b.append("\\n");
        case '\r' -> b.append("\\r"); case '\t' -> b.append("\\t");
        default -> { if (c < 0x20) b.append(String.format("\\u%04x", (int) c)); else b.append(c); } }
      return b.append('"').toString();
    }
    if (o instanceof Map<?, ?> m) {
      var j = new StringJoiner(",", "{", "}");
      m.forEach((k, v) -> j.add(js(String.valueOf(k)) + ":" + js(v)));
      return j.toString();
    }
    if (o instanceof List<?> l) { var j = new StringJoiner(",", "[", "]"); l.forEach(x -> j.add(js(x))); return j.toString(); }
    return o.toString();
  }

  // ---------- storage ----------
  @SuppressWarnings("unchecked")
  static List<Map<String, Object>> tbl(String k) { return (List<Map<String, Object>>) db.get(k); }
  static synchronized void save() {
    try {
      if (JdbcStore.enabled()) { JdbcStore.saveState(db); return; }
      Path tmp = Path.of(DB + ".tmp"); Files.writeString(tmp, js(db));
      Files.move(tmp, DB, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    } catch (Exception e) { e.printStackTrace(); throw new Err(500, "Could not save data: " + e.getMessage()); }
  }
  @SuppressWarnings("unchecked")
  static void load() throws IOException {
    if (JdbcStore.enabled()) {
      try { db = JdbcStore.loadOrMigrate(DB); }
      catch (Exception e) { throw new IOException("MySQL storage failed: " + e.getMessage(), e); }
    } else db = Files.exists(DB) ? (Map<String, Object>) new P(Files.readString(DB)).v() : new LinkedHashMap<>();
    for (String k : List.of("users", "projects", "tasks")) db.putIfAbsent(k, new ArrayList<>());
    db.putIfAbsent("sessions", new LinkedHashMap<>());
    // Backward-compatible role migration for existing JSON databases.
    boolean changed = false;
    for (int i = 0; i < tbl("users").size(); i++) {
      Map<String, Object> u = tbl("users").get(i);
      if (!u.containsKey("role")) { u.put("role", i == 0 ? "admin" : "member"); changed = true; }
      if (!u.containsKey("created")) { u.put("created", Instant.now().toString()); changed = true; }
    }
    if (changed) save();
  }
  static Map<String, Object> find(String t, Object id) {
    return tbl(t).stream().filter(x -> x.get("id").equals(id)).findFirst().orElseThrow(() -> new Err(404, "Not found"));
  }
  static String rid() { byte[] b = new byte[6]; RND.nextBytes(b); return HexFormat.of().formatHex(b); }
  static String hash(String pw, String salt) {
    try { var f = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
      return HexFormat.of().formatHex(f.generateSecret(new PBEKeySpec(pw.toCharArray(), HexFormat.of().parseHex(salt), 120000, 256)).getEncoded()); }
    catch (Exception e) { throw new RuntimeException(e); }
  }
  static String str(Map<String, Object> b, String k, int max) {
    Object v = b.get(k); String s = v == null ? "" : v.toString().trim();
    if (s.length() > max) throw new Err(400, k + " is too long");
    return s;
  }
  static Map<String, Object> pub(Map<String, Object> u) {
    var out = new LinkedHashMap<String, Object>();
    out.put("id", u.get("id")); out.put("name", u.get("name")); out.put("email", u.get("email"));
    out.put("role", u.getOrDefault("role", "member")); out.put("created", u.getOrDefault("created", ""));
    return out;
  }
  static void admin(Map<String, Object> u) {
    // Runtime polymorphism: the selected policy controls access to administrative operations.
    RolePolicy policy = "admin".equals(u.getOrDefault("role", "member")) ? new AdminRolePolicy() : new MemberRolePolicy();
    if (!policy.canManageUsers(u)) throw new Err(403, policy.roleName() + " role cannot access this operation");
  }

  @SuppressWarnings("unchecked")
  static Map<String, Object> session(Map<String, Object> user) {
    String tok = rid() + rid() + rid();
    ((Map<String, Object>) db.get("sessions")).put(tok, user.get("id")); save();
    return Map.of("token", tok, "user", pub(user));
  }

  // ---------- routing ----------
  @SuppressWarnings("unchecked")
  static synchronized Object route(String m, String[] s, Map<String, Object> b, String auth) {
    if (m.equals("POST") && s.length == 1 && s[0].equals("register")) {
      String name = str(b, "name", 60), email = str(b, "email", 120).toLowerCase(), pw = str(b, "password", 200);
      if (name.isEmpty() || !email.matches("[^@\\s]+@[^@\\s]+\\.[^@\\s]+")) throw new Err(400, "Enter a name and a valid email");
      if (pw.length() < 6) throw new Err(400, "Password needs at least 6 characters");
      if (tbl("users").stream().anyMatch(u -> u.get("email").equals(email))) throw new Err(409, "Email already registered");
      byte[] sb = new byte[16]; RND.nextBytes(sb); String salt = HexFormat.of().formatHex(sb);
      var u = new LinkedHashMap<String, Object>(Map.of("id", rid(), "name", name, "email", email, "salt", salt, "hash", hash(pw, salt),
        "role", tbl("users").isEmpty() ? "admin" : "member", "created", Instant.now().toString()));
      tbl("users").add(u); save(); return session(u);
    }
    if (m.equals("POST") && s.length == 1 && s[0].equals("login")) {
      String email = str(b, "email", 120).toLowerCase(), pw = str(b, "password", 200);
      var u = tbl("users").stream().filter(x -> x.get("email").equals(email)).findFirst().orElse(null);
      if (u == null || !hash(pw, (String) u.get("salt")).equals(u.get("hash"))) throw new Err(401, "Wrong email or password");
      return session(u);
    }
    Object uid = auth == null ? null : ((Map<String, Object>) db.get("sessions")).get(auth.replace("Bearer ", ""));
    if (uid == null) throw new Err(401, "Sign in to continue");
    var me = find("users", uid);

    // Admin APIs: all administrative actions are enforced server-side.
    if (s[0].equals("admin")) {
      admin(me);
      if (s.length == 1 && m.equals("GET")) {
        long activeUsers = tbl("users").size();
        long doneTasks = tbl("tasks").stream().filter(t -> "done".equals(t.get("status"))).count();
        long overdue = tbl("tasks").stream().filter(t -> !"done".equals(t.get("status")) && t.get("due") != null && !t.get("due").toString().isBlank() && t.get("due").toString().compareTo(java.time.LocalDate.now().toString()) < 0).count();
        return Map.of("users", activeUsers, "projects", tbl("projects").size(), "tasks", tbl("tasks").size(), "completedTasks", doneTasks, "overdueTasks", overdue,
          "members", tbl("users").stream().map(Main::pub).toList());
      }
      if (s.length == 2 && s[1].equals("users") && m.equals("GET")) return tbl("users").stream().map(Main::pub).toList();
      if (s.length == 2 && s[1].equals("users") && m.equals("POST")) {
        String name = str(b, "name", 60), email = str(b, "email", 120).toLowerCase(), pw = str(b, "password", 200);
        String role = str(b, "role", 20);
        if (name.isBlank() || !email.matches("[^@\\s]+@[^@\\s]+\\.[^@\\s]+")) throw new Err(400, "Enter a name and a valid email");
        if (pw.length() < 8) throw new Err(400, "Initial password must be at least 8 characters");
        if (!Set.of("admin", "member").contains(role)) throw new Err(400, "Role must be admin or member");
        if (tbl("users").stream().anyMatch(u -> email.equals(u.get("email")))) throw new Err(409, "Email already registered");
        byte[] sb = new byte[16]; RND.nextBytes(sb); String salt = HexFormat.of().formatHex(sb);
        var added = new LinkedHashMap<String, Object>(); added.put("id", rid()); added.put("name", name); added.put("email", email);
        added.put("salt", salt); added.put("hash", hash(pw, salt)); added.put("role", role); added.put("created", Instant.now().toString());
        tbl("users").add(added); save(); return pub(added);
      }
      if (s.length == 3 && s[1].equals("users") && m.equals("DELETE")) {
        var target = find("users", s[2]);
        if (target.get("id").equals(me.get("id"))) throw new Err(400, "You cannot remove your own account while signed in");
        if ("admin".equals(target.getOrDefault("role", "member")) && tbl("users").stream().filter(u -> "admin".equals(u.getOrDefault("role", "member"))).count() <= 1) throw new Err(400, "At least one administrator is required");
        tbl("users").remove(target);
        ((Map<String, Object>) db.get("sessions")).entrySet().removeIf(e -> target.get("id").equals(e.getValue()));
        for (var task : tbl("tasks")) if (target.get("id").equals(task.get("assignee"))) task.put("assignee", "");
        save(); return Map.of("ok", true);
      }
      if (s.length == 4 && s[1].equals("users") && s[3].equals("role") && m.equals("PUT")) {
        var target = find("users", s[2]); String role = str(b, "role", 20);
        if (!Set.of("admin", "member").contains(role)) throw new Err(400, "Role must be admin or member");
        if (target.get("id").equals(me.get("id")) && !role.equals("admin")) throw new Err(400, "You cannot remove your own admin role");
        if ("admin".equals(target.getOrDefault("role", "member")) && role.equals("member") && tbl("users").stream().filter(u -> "admin".equals(u.getOrDefault("role", "member"))).count() <= 1) throw new Err(400, "At least one administrator is required");
        target.put("role", role); save(); return pub(target);
      }
      throw new Err(404, "No such admin endpoint");
    }
    if (s[0].equals("reports") && s.length == 2 && s[1].equals("summary") && m.equals("GET")) {
      // Build report on a worker thread so expensive calculations do not block the HTTP request pool.
      try {
        return ReportService.summaryAsync(new ArrayList<>(tbl("projects")), new ArrayList<>(tbl("tasks")))
          .get(10, java.util.concurrent.TimeUnit.SECONDS);
      } catch (Exception e) { throw new Err(500, "Report generation failed"); }
    }
    if (s[0].equals("users") && m.equals("GET")) return tbl("users").stream().map(Main::pub).toList();
    if (s[0].equals("projects")) {
      if (s.length == 1 && m.equals("GET")) {
        var out = new ArrayList<Map<String, Object>>();
        for (var p : tbl("projects")) { var c = new LinkedHashMap<>(p);
          var ts = tbl("tasks").stream().filter(t -> t.get("project").equals(p.get("id"))).toList();
          c.put("total", ts.size()); c.put("done", ts.stream().filter(t -> t.get("status").equals("done")).count()); out.add(c); }
        return out;
      }
      if (s.length == 1 && m.equals("POST")) {
        String n = str(b, "name", 80); if (n.isEmpty()) throw new Err(400, "Project name is required");
        var p = new LinkedHashMap<String, Object>(Map.of("id", rid(), "name", n, "description", str(b, "description", 500),
          "owner", uid, "created", Instant.now().toString()));
        tbl("projects").add(p); save(); return p;
      }
      var p = find("projects", s[1]);
      if (s.length == 2 && m.equals("DELETE")) {
        if (!p.get("owner").equals(uid)) throw new Err(403, "Only the project owner can delete it");
        tbl("projects").remove(p); tbl("tasks").removeIf(t -> t.get("project").equals(p.get("id"))); save(); return Map.of("ok", true);
      }
      if (s.length == 3 && s[2].equals("tasks") && m.equals("GET"))
        return tbl("tasks").stream().filter(t -> t.get("project").equals(p.get("id"))).toList();
      if (s.length == 3 && s[2].equals("tasks") && m.equals("POST")) {
        var t = new LinkedHashMap<String, Object>();
        t.put("id", rid()); t.put("project", p.get("id")); t.put("title", ""); t.put("description", "");
        t.put("status", "todo"); t.put("priority", "medium"); t.put("assignee", ""); t.put("due", "");
        t.put("creator", uid); t.put("created", Instant.now().toString()); t.put("comments", new ArrayList<>());
        applyTask(t, b); if (t.get("title").toString().isEmpty()) throw new Err(400, "Task title is required");
        tbl("tasks").add(t); save(); return t;
      }
    }
    if (s[0].equals("tasks") && s.length >= 2) {
      var t = find("tasks", s[1]);
      if (s.length == 2 && m.equals("PUT")) { applyTask(t, b);
        if (t.get("title").toString().isEmpty()) throw new Err(400, "Task title is required"); save(); return t; }
      if (s.length == 2 && m.equals("DELETE")) { tbl("tasks").remove(t); save(); return Map.of("ok", true); }
      if (s.length == 3 && s[2].equals("comments") && m.equals("POST")) {
        String txt = str(b, "text", 1000); if (txt.isEmpty()) throw new Err(400, "Write a comment first");
        ((List<Object>) t.get("comments")).add(Map.of("user", me.get("name"), "text", txt, "at", Instant.now().toString()));
        save(); return t;
      }
    }
    throw new Err(404, "No such endpoint");
  }

  static void applyTask(Map<String, Object> t, Map<String, Object> b) {
    if (b.containsKey("title")) t.put("title", str(b, "title", 140));
    if (b.containsKey("description")) t.put("description", str(b, "description", 2000));
    if (b.containsKey("status")) { String v = str(b, "status", 10); if (!STATUS.contains(v)) throw new Err(400, "Invalid status"); t.put("status", v); }
    if (b.containsKey("priority")) { String v = str(b, "priority", 10); if (!PRIO.contains(v)) throw new Err(400, "Invalid priority"); t.put("priority", v); }
    if (b.containsKey("assignee")) { String v = str(b, "assignee", 20); if (!v.isEmpty()) find("users", v); t.put("assignee", v); }
    if (b.containsKey("due")) { String v = str(b, "due", 10); if (!v.isEmpty() && !v.matches("\\d{4}-\\d{2}-\\d{2}")) throw new Err(400, "Invalid date"); t.put("due", v); }
  }

  // ---------- http ----------
  static void send(HttpExchange ex, int code, String type, byte[] body) throws IOException {
    ex.getResponseHeaders().set("Content-Type", type);
    ex.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
    ex.sendResponseHeaders(code, body.length);
    try (var o = ex.getResponseBody()) { o.write(body); }
  }

  @SuppressWarnings("unchecked")
  static void handle(HttpExchange ex) throws IOException {
    String path = ex.getRequestURI().getPath(), m = ex.getRequestMethod();
    try {
      if (!path.startsWith("/api/")) {
        Path f = Path.of("public/index.html");
        send(ex, 200, "text/html; charset=utf-8", Files.readAllBytes(f)); return;
      }
      String raw = new String(ex.getRequestBody().readNBytes(1_000_000), StandardCharsets.UTF_8);
      Map<String, Object> body = Map.of();
      if (!raw.isBlank()) try { body = (Map<String, Object>) new P(raw).v(); } catch (RuntimeException e) { throw new Err(400, "Invalid JSON"); }
      Object r = route(m, path.substring(5).split("/"), body, ex.getRequestHeaders().getFirst("Authorization"));
      send(ex, 200, "application/json", js(r).getBytes(StandardCharsets.UTF_8));
    } catch (Err e) {
      send(ex, e.c, "application/json", js(Map.of("error", e.getMessage())).getBytes(StandardCharsets.UTF_8));
    } catch (Exception e) {
      e.printStackTrace();
      send(ex, 500, "application/json", js(Map.of("error", "Server error")).getBytes(StandardCharsets.UTF_8));
    }
  }

  public static void main(String[] a) throws Exception {
    load();
    int port = Integer.parseInt(System.getenv().getOrDefault("PORT", "8080"));
    var srv = HttpServer.create(new InetSocketAddress(port), 0);
    srv.createContext("/", Main::handle);
    srv.setExecutor(java.util.concurrent.Executors.newFixedThreadPool(Math.max(4, Runtime.getRuntime().availableProcessors())));
    BackgroundJobs.start();
    srv.start();
    System.out.println("Spades PM by Team Spades running on http://localhost:" + port);
    System.out.println("Storage: " + (JdbcStore.enabled() ? "MySQL JDBC" : "JSON (default)"));
  }
}
