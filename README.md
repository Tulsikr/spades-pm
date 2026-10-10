# Spades PM — Java Backend + Web GUI + MySQL JDBC

**Build by Team Spades**

This version adapts the existing Spades PM web application to the 33-mark Java project rubric while retaining the current frontend and the existing JSON-file mode. MySQL JDBC is an opt-in storage mode, and existing `data.json` is imported automatically on the first MySQL startup if the database has no users yet.

## Requirements
- JDK 17 or newer
- For MySQL mode: MySQL Server 8+, Maven 3.9+, and network access the first time Maven downloads MySQL Connector/J
- VS Code (recommended)

## A. Quick run without changing the current data format
This keeps JSON persistence and does not require MySQL or Maven:

1. Open the `spades-pm-upgraded` folder in VS Code.
2. Open Terminal → New Terminal.
3. Run `javac *.java`
4. Run `java Main`
5. Visit http://localhost:8080

Or double-click `run.bat`.

## B. Enable MySQL JDBC
1. Back up your existing `data.json` and do not delete it.
2. Start MySQL Server.
3. Open MySQL Workbench or the MySQL command line and run `database.sql` (it creates the `spades_pm` database).
4. In the same PowerShell terminal used to start the app, set your database credentials:

```powershell
$env:SPADES_STORAGE = "mysql"
$env:SPADES_DB_URL = "jdbc:mysql://localhost:3306/spades_pm?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC"
$env:SPADES_DB_USER = "root"
$env:SPADES_DB_PASSWORD = "YOUR_MYSQL_PASSWORD"
mvn compile exec:java
```

Use your actual MySQL username/password. Do not put real passwords into source files or commit them to Git. The JDBC driver is declared in `pom.xml`; Maven downloads it on the first run.

If the MySQL tables are empty and `data.json` exists, the application imports the old users, projects, tasks and sessions into MySQL. The JSON file is not deleted or modified by the import. Once MySQL contains users, MySQL becomes the active source of truth for that mode. Switch back to JSON by opening a new terminal and leaving `SPADES_STORAGE` unset (or setting it to `json`).

## What is implemented for the marking rubric

1. **OOP — 10 marks:** `RolePolicy` interface; `BaseRolePolicy` inheritance with `AdminRolePolicy` and `MemberRolePolicy` subclasses; runtime polymorphism in backend authorization; custom `Main.Err` exception handling; encapsulated JDBC and service classes.
2. **Collections & Generics — 6 marks:** typed `List<Map<String,Object>>`, `Map<String,Object>`, `Set`, generic `EntityDAO<T>`, and generic collection APIs.
3. **Multithreading & Synchronization — 4 marks:** HTTP server worker pool, asynchronous report calculations in `ReportService`, scheduled background overdue-task checks in `BackgroundJobs`, and synchronized route/state persistence methods.
4. **Database operation classes — 7 marks:** `UserDAO`, `ProjectDAO`, `TaskDAO`, `JdbcStore` and `DBConnection`; each entity DAO performs prepared-statement reads and batch writes.
5. **JDBC connectivity — 3 marks:** `DBConnection` uses `DriverManager` and environment-based connection settings; MySQL Connector/J is declared in Maven.
6. **JDBC implementation — 3 marks:** schema creation, prepared statements, batch inserts, transaction commit/rollback, indexes, and relational tables with JSON payloads to preserve existing fields/comments.

The UI continues using the same `/api` routes. The report summary is also available at `GET /api/reports/summary` after login.

## Data and security notes
- Always keep a backup of `data.json` before migration.
- Passwords are stored as salted PBKDF2 hashes, not plain text.
- MySQL credentials must be set through environment variables.
- This is a student project starter, not production-ready multi-tenant software. Review session expiry/revocation, CSRF/CORS, rate limiting, audit logs and deployment security before public hosting.

## Files added
- `DBConnection.java` — JDBC connection factory.
- `EntityDAO.java` — generic DAO interface.
- `UserDAO.java`, `ProjectDAO.java`, `TaskDAO.java` — entity persistence.
- `JdbcStore.java` — schema setup, migration and transaction-managed persistence.
- `RolePolicy.java`, `AdminRolePolicy.java`, `MemberRolePolicy.java` — interface, inheritance and polymorphism.
- `ReportService.java`, `BackgroundJobs.java` — worker pool and scheduled background processing.
- `pom.xml`, `database.sql`, `.env.example` — dependency, schema and configuration templates.

## Troubleshooting
- `javac is not recognized`: install JDK 17+ and configure PATH.
- `mvn is not recognized`: install Maven 3.9+ for MySQL mode; JSON mode only needs the JDK.
- `Communications link failure`: start MySQL Server and check the host/port.
- `Unknown database 'spades_pm'`: run `database.sql` first.
- `Access denied`: check `SPADES_DB_USER` and `SPADES_DB_PASSWORD`.
- `Address already in use`: stop the other server or set `$env:PORT = "8081"` before starting.


## UI polish update
- Dashboard statistic cards are keyboard- and mouse-clickable shortcuts.
- Header and sidebar profile controls open a profile dialog with account role and sign-out action.
- Removed the AI page disclaimer paragraph requested for the interface.
- Removed the Admin Panel System overview and Administration notes cards; member management remains.
