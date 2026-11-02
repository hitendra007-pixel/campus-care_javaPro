import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.Executors;

/**
 * CampusCare - Java backend for the website.
 * Java handles login, complaint creation, ticket access, status changes,
 * department messages, persistent file storage and serving the frontend pages.
 * Supports production deployment with dynamic PORT, persistent storage,
 * and multi-device access.
 * Run: javac CampusComplaintSystem.java && java CampusComplaintSystem
 */
public class CampusComplaintSystem {
    private static final Object FILE_LOCK = new Object();
    private static final String[] STUDENT_IDS = {
        "AP25110071330", "AP25110071331", "AP25110071332",
        "AP25110071333", "AP25110071334", "AP25110071335"
    };
    private static final String[] STUDENT_NAMES = {
        "Hitendra", "Sasank", "Lokesh", "Srikanta", "Abhinav", "Prabhas"
    };

    /**
     * Resolves the server port from the environment (e.g. Railway, Render, Cloud Run),
     * system property, or default 8090.
     */
    private static int getPort() {
        for (String var : new String[]{"PORT", "SERVER_PORT", "HTTP_PORT"}) {
            String envPort = System.getenv(var);
            if (envPort != null && !envPort.trim().isEmpty()) {
                try {
                    return Integer.parseInt(envPort.trim());
                } catch (NumberFormatException ignored) {}
            }
        }
        for (String prop : new String[]{"campuscare.port", "server.port", "port"}) {
            String sysPort = System.getProperty(prop);
            if (sysPort != null && !sysPort.trim().isEmpty()) {
                try {
                    return Integer.parseInt(sysPort.trim());
                } catch (NumberFormatException ignored) {}
            }
        }
        return 8090;
    }

    /**
     * Resolves the persistent file storage path.
     * Supports persistent disks mounted at DATA_DIR (e.g. /data on Render or Railway volume)
     * or custom COMPLAINT_FILE path, falling back to complaints.txt in the application directory.
     */
    private static Path getComplaintFilePath() {
        String envFile = System.getenv("COMPLAINT_FILE");
        if (envFile != null && !envFile.trim().isEmpty()) {
            return Paths.get(envFile.trim()).toAbsolutePath().normalize();
        }
        String dataDir = System.getenv("DATA_DIR");
        if (dataDir != null && !dataDir.trim().isEmpty()) {
            return Paths.get(dataDir.trim(), "complaints.txt").toAbsolutePath().normalize();
        }
        Path root = getStaticRoot();
        if (Files.exists(root.resolve("complaints.txt"))) {
            return root.resolve("complaints.txt").toAbsolutePath().normalize();
        }
        Path inSub = Paths.get("CampusCare", "complaints.txt").toAbsolutePath().normalize();
        if (Files.exists(inSub)) {
            return inSub;
        }
        return Paths.get("complaints.txt").toAbsolutePath().normalize();
    }

    /**
     * Initializes the storage directory and seeds initial data if moving to a persistent volume.
     */
    private static void initStorage() {
        synchronized (FILE_LOCK) {
            Path target = getComplaintFilePath();
            try {
                if (target.getParent() != null && !Files.exists(target.getParent())) {
                    Files.createDirectories(target.getParent());
                }
                // If persistent storage is empty/new, try copying existing local seed data
                if (!Files.exists(target)) {
                    Path root = getStaticRoot();
                    Path[] seeds = {
                        root.resolve("complaints.txt"),
                        Paths.get("CampusCare", "complaints.txt"),
                        Paths.get("complaints.txt")
                    };
                    boolean seeded = false;
                    for (Path seed : seeds) {
                        Path absSeed = seed.toAbsolutePath().normalize();
                        if (!absSeed.equals(target) && Files.exists(absSeed) && Files.size(absSeed) > 0) {
                            Files.copy(absSeed, target, StandardCopyOption.COPY_ATTRIBUTES);
                            System.out.println("Seeded persistent complaints file from: " + absSeed);
                            seeded = true;
                            break;
                        }
                    }
                    if (!seeded) {
                        Files.createFile(target);
                    }
                }
                if (Files.exists(target) && Files.size(target) == 0) {
                    List<String> defaultSeed = Arrays.asList(
                        "1|AP25110071330|Hitendra|Transport|Bus is late and seats need maintenance|In Progress|Staff: We have scheduled maintenance for this bus",
                        "2|AP25110071330|Hitendra|IT Support|Wi-Fi connection drops in campus library|Pending|",
                        "3|AP25110071331|Sasank|Hostel|Hot water supply issue on 3rd floor|Resolved|Staff: Boiler heating element replaced"
                    );
                    Files.write(target, defaultSeed, StandardCharsets.UTF_8, StandardOpenOption.TRUNCATE_EXISTING);
                    System.out.println("Populated default complaints seed in: " + target);
                }
            } catch (IOException e) {
                System.err.println("Notice: Initializing complaints storage at " + target + ": " + e.getMessage());
            }
        }
    }

    // 1. LOGIN LOGIC: Java verifies student, staff and administrator accounts.
    private static String studentName(String registrationNumber) {
        for (int i = 0; i < STUDENT_IDS.length; i++) {
            if (STUDENT_IDS[i].equals(registrationNumber)) return STUDENT_NAMES[i];
        }
        return "Student";
    }

    private static boolean studentLogin(String registrationNumber, String password) {
        for (String id : STUDENT_IDS) {
            if (id.equals(registrationNumber) && "student123".equals(password)) return true;
        }
        return false;
    }

    private static String staffDepartment(String username, String password) {
        if (username.equals("transport") && password.equals("bus123")) return "Transport";
        if (username.equals("hostel") && password.equals("host123")) return "Hostel";
        if (username.equals("itstaff") && password.equals("it123")) return "IT Support";
        if (username.equals("finance") && password.equals("fee123")) return "Finance";
        if (username.equals("general") && password.equals("gen123")) return "General Administration";
        return "";
    }

    // 2. DEPARTMENT LOGIC: Java assigns each complaint to the correct department.
    private static String departmentFor(String category) {
        switch (category) {
            case "Bus Problem": return "Transport";
            case "Hostel Problem": return "Hostel";
            case "Wi-Fi / Computer": return "IT Support";
            case "Fee Problem": return "Finance";
            case "Classroom / Academic":
            case "Other": return "General Administration";
            default: return "";
        }
    }

    // 3. FILE HANDLING: Java saves and reads complaint records from a thread-safe persistent file.
    // Record format: ID|registration|name|department|description|status|message
    private static List<String[]> readComplaints() throws IOException {
        synchronized (FILE_LOCK) {
            List<String[]> records = new ArrayList<>();
            Path file = getComplaintFilePath();
            if (!Files.exists(file)) return records;
            for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                if (!line.trim().isEmpty()) records.add(line.split("\\|", 7));
            }
            return records;
        }
    }

    private static void saveComplaints(List<String[]> records) throws IOException {
        synchronized (FILE_LOCK) {
            Path file = getComplaintFilePath();
            if (file.getParent() != null && !Files.exists(file.getParent())) {
                Files.createDirectories(file.getParent());
            }
            List<String> lines = new ArrayList<>();
            for (String[] record : records) lines.add(String.join("|", record));
            Files.write(file, lines, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        }
    }

    private static int nextComplaintId(List<String[]> records) {
        int next = 1;
        for (String[] record : records) {
            try { next = Math.max(next, Integer.parseInt(record[0]) + 1); }
            catch (Exception ignored) { }
        }
        return next;
    }

    private static String safeDecode(String s) {
        if (s == null) return "";
        try {
            return URLDecoder.decode(s, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return s;
        }
    }

    // 4. HTTP COMMUNICATION: receives browser form data and sends JSON responses.
    private static Map<String, String> readParameters(HttpExchange exchange) throws IOException {
        String raw;
        if ("GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            raw = exchange.getRequestURI().getRawQuery();
        } else {
            raw = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        }
        Map<String, String> values = new HashMap<>();
        if (raw != null && !raw.isEmpty()) {
            for (String pair : raw.split("&")) {
                String[] parts = pair.split("=", 2);
                if (parts.length == 2) {
                    values.put(safeDecode(parts[0]), safeDecode(parts[1]));
                } else if (parts.length == 1 && !parts[0].isEmpty()) {
                    values.put(safeDecode(parts[0]), "");
                }
            }
        }
        return values;
    }

    private static String value(Map<String, String> values, String key) {
        return values.getOrDefault(key, "").trim();
    }

    private static String json(String value) {
        if (value == null) return "\"\"";
        return "\"" + value.replace("\\", "\\\\")
                           .replace("\"", "\\\"")
                           .replace("\r", " ")
                           .replace("\n", " ")
                           .replace("\t", " ") + "\"";
    }

    private static void sendJson(HttpExchange exchange, int status, String response) throws IOException {
        byte[] data = response.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type, Authorization");
        exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        exchange.sendResponseHeaders(status, data.length);
        exchange.getResponseBody().write(data);
        exchange.close();
    }

    // 5. LOGIN API: browser sends username/password; Java returns the user role.
    private static void login(HttpExchange exchange, Map<String, String> data) throws IOException {
        String username = value(data, "username");
        String password = value(data, "password");
        String role = "", department = "", name = "";

        if (username.equals("admin") && password.equals("admin123")) {
            role = "admin"; name = "Administrator";
        } else if (studentLogin(username, password)) {
            role = "student"; name = studentName(username);
        } else {
            department = staffDepartment(username, password);
            if (!department.isEmpty()) { role = "staff"; name = department + " Staff"; }
        }

        if (role.isEmpty()) {
            sendJson(exchange, 401, "{\"error\":\"Invalid username or password\"}");
        } else {
            sendJson(exchange, 200, "{\"id\":" + json(username) + ",\"role\":" + json(role)
                    + ",\"dept\":" + json(department) + ",\"name\":" + json(name) + "}");
        }
    }

    // 6. COMPLAINT API: Java validates, assigns department, creates ID and stores ticket.
    private static void createComplaint(HttpExchange exchange, Map<String, String> data) throws IOException {
        String username = value(data, "username");
        String password = value(data, "password");
        String category = value(data, "category");
        String description = value(data, "description").replace("|", "/");
        String department = departmentFor(category);

        if (!studentLogin(username, password) || description.isEmpty() || department.isEmpty()) {
            sendJson(exchange, 400, "{\"error\":\"Check student login, category and complaint description\"}");
            return;
        }

        List<String[]> records = readComplaints();
        int id = nextComplaintId(records);
        records.add(new String[]{String.valueOf(id), username, studentName(username),
                department, description, "Pending", ""});
        saveComplaints(records);
        sendJson(exchange, 200, "{\"id\":" + json(String.valueOf(id)) + ",\"status\":\"Pending\"}");
    }

    // 7. TICKET DISPLAY API: Java returns only the records relevant to that portal.
    private static void listTickets(HttpExchange exchange, Map<String, String> query) throws IOException {
        String username = value(query, "username");
        String role = value(query, "role");
        String department = value(query, "department");
        StringBuilder result = new StringBuilder("[");
        boolean first = true;

        for (String[] record : readComplaints()) {
            if (record.length < 6) continue;
            boolean visible = role.equals("admin")
                    || (role.equals("student") && record[1].equals(username))
                    || (role.equals("staff") && record[3].equalsIgnoreCase(department));
            if (!visible) continue;
            if (!first) result.append(',');
            first = false;
            String message = record.length > 6 ? record[6] : "";
            result.append("{\"id\":").append(json(record[0]))
                    .append(",\"student\":").append(json(record[1]))
                    .append(",\"studentName\":").append(json(record[2]))
                    .append(",\"dept\":").append(json(record[3]))
                    .append(",\"category\":").append(json(record[3]))
                    .append(",\"description\":").append(json(record[4]))
                    .append(",\"status\":").append(json(record[5]))
                    .append(",\"reply\":").append(json(message))
                    .append(",\"raisedAt\":").append(json("2026-11-01T00:00:00Z"))
                    .append(",\"updates\":[]}");
        }
        sendJson(exchange, 200, result.append(']').toString());
    }

    // 8. STATUS API: department staff update their department's tickets; admin can close.
    private static void updateStatus(HttpExchange exchange, Map<String, String> data) throws IOException {
        String id = value(data, "ticketId");
        String status = value(data, "status");
        String role = value(data, "role");
        String department = value(data, "department");
        if (!(status.equals("Pending") || status.equals("In Progress")
                || status.equals("Resolved") || (role.equals("admin") && status.equals("Closed by Admin")))) {
            sendJson(exchange, 400, "{\"error\":\"Invalid status\"}"); return;
        }
        List<String[]> records = readComplaints();
        boolean updated = false;
        for (String[] record : records) {
            if (record.length >= 6 && record[0].equals(id)
                    && (role.equals("admin") || (role.equals("staff") && record[3].equalsIgnoreCase(department)))) {
                record[5] = status; updated = true;
            }
        }
        if (updated) saveComplaints(records);
        sendJson(exchange, updated ? 200 : 403,
                updated ? "{\"ok\":true}" : "{\"error\":\"Ticket not found or access denied\"}");
    }

    // 9. MESSAGE API: student, assigned staff, or administrator can add a message to a ticket.
    private static void sendMessage(HttpExchange exchange, Map<String, String> data) throws IOException {
        String id = value(data, "ticketId");
        String message = value(data, "message").replace("|", "/");
        String role = value(data, "role");
        String department = value(data, "department");
        String username = value(data, "username");
        if (message.isEmpty()) { sendJson(exchange, 400, "{\"error\":\"Message cannot be empty\"}"); return; }

        List<String[]> records = readComplaints();
        boolean updated = false;
        for (int i = 0; i < records.size(); i++) {
            String[] record = records.get(i);
            boolean allowed = role.equals("admin")
                    || (role.equals("staff") && record.length >= 4 && record[3].equalsIgnoreCase(department))
                    || (role.equals("student") && record.length >= 2 && record[1].equals(username));
            if (record.length >= 6 && record[0].equals(id) && allowed) {
                if (record.length < 7) record = Arrays.copyOf(record, 7);
                String prefix = role.equals("student") ? "Student: "
                              : role.equals("admin") ? "Admin: "
                              : "Staff: ";
                record[6] = prefix + message;
                records.set(i, record); updated = true;
            }
        }
        if (updated) saveComplaints(records);
        sendJson(exchange, updated ? 200 : 403,
                updated ? "{\"ok\":true}" : "{\"error\":\"Ticket not found or access denied\"}");
    }

    /**
     * Finds the base directory containing website static files (index.html, style.css, etc.).
     * Searches current directory, subfolders, classpath parent folders, and environment variables.
     */
    private static Path getStaticRoot() {
        String envStatic = System.getenv("STATIC_DIR");
        if (envStatic != null && !envStatic.trim().isEmpty()) {
            Path p = Paths.get(envStatic.trim()).toAbsolutePath().normalize();
            if (Files.isDirectory(p) && Files.exists(p.resolve("index.html"))) return p;
        }

        List<Path> candidates = new ArrayList<>();
        Path cwd = Paths.get(".").toAbsolutePath().normalize();
        candidates.add(cwd);
        candidates.add(cwd.resolve("CampusCare"));

        try {
            Path codePath = Paths.get(CampusComplaintSystem.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            if (Files.isDirectory(codePath)) {
                candidates.add(codePath.normalize());
                candidates.add(codePath.resolve("CampusCare").normalize());
            }
            if (codePath.getParent() != null) {
                candidates.add(codePath.getParent().normalize());
                candidates.add(codePath.getParent().resolve("CampusCare").normalize());
                if (codePath.getParent().getParent() != null) {
                    candidates.add(codePath.getParent().getParent().normalize());
                    candidates.add(codePath.getParent().getParent().resolve("CampusCare").normalize());
                }
            }
        } catch (Exception ignored) {}

        for (Path candidate : candidates) {
            if (candidate != null && Files.exists(candidate.resolve("index.html"))) {
                return candidate.toAbsolutePath().normalize();
            }
        }
        return cwd;
    }

    // 10. FRONTEND SERVER: Java serves HTML, CSS, JavaScript, images and static assets.
    private static void serveWebsite(HttpExchange exchange) throws IOException {
        String requested = exchange.getRequestURI().getPath();
        if (requested == null || requested.isEmpty() || requested.equals("/")) {
            requested = "/index.html";
        }
        if (requested.startsWith("/")) {
            requested = requested.substring(1);
        }

        Path root = getStaticRoot();
        Path file = root.resolve(requested).normalize();

        // Handle favicon fallback
        if ((requested.equalsIgnoreCase("favicon.ico") || requested.toLowerCase().endsWith("/favicon.ico")) && !Files.exists(file)) {
            Path icon = root.resolve("srm-ap-logo.png");
            if (Files.exists(icon)) {
                file = icon;
                requested = "srm-ap-logo.png";
            }
        }

        if (!file.startsWith(root) || !Files.exists(file) || Files.isDirectory(file)) {
            exchange.sendResponseHeaders(404, -1);
            exchange.close();
            return;
        }

        String lower = requested.toLowerCase();
        String type;
        if (lower.endsWith(".css")) {
            type = "text/css; charset=utf-8";
        } else if (lower.endsWith(".js")) {
            type = "application/javascript; charset=utf-8";
        } else if (lower.endsWith(".png")) {
            type = "image/png";
        } else if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) {
            type = "image/jpeg";
        } else if (lower.endsWith(".svg")) {
            type = "image/svg+xml";
        } else if (lower.endsWith(".ico")) {
            type = "image/x-icon";
        } else if (lower.endsWith(".json")) {
            type = "application/json; charset=utf-8";
        } else if (lower.endsWith(".txt")) {
            type = "text/plain; charset=utf-8";
        } else {
            type = "text/html; charset=utf-8";
        }

        byte[] content = Files.readAllBytes(file);
        exchange.getResponseHeaders().set("Content-Type", type);
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        if ("HEAD".equalsIgnoreCase(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(200, -1);
        } else {
            exchange.sendResponseHeaders(200, content.length);
            exchange.getResponseBody().write(content);
        }
        exchange.close();
    }

    // MAIN: starts Java's web server. Binds externally to 0.0.0.0 and supports multi-threading.
    public static void main(String[] args) throws Exception {
        initStorage();
        int port = getPort();
        HttpServer server = HttpServer.create(new InetSocketAddress("0.0.0.0", port), 0);
        server.setExecutor(Executors.newCachedThreadPool());

        // API endpoints
        server.createContext("/api/login", exchange -> handle(exchange, CampusComplaintSystem::login));
        server.createContext("/api/tickets", exchange -> handle(exchange, (x, data) -> {
            if ("GET".equalsIgnoreCase(x.getRequestMethod())) listTickets(x, data);
            else createComplaint(x, data);
        }));
        server.createContext("/api/status", exchange -> handle(exchange, CampusComplaintSystem::updateStatus));
        server.createContext("/api/message", exchange -> handle(exchange, CampusComplaintSystem::sendMessage));
        server.createContext("/api/health", exchange -> {
            sendJson(exchange, 200, "{\"status\":\"ok\",\"service\":\"CampusCare\"}");
        });

        // Static frontend serving
        server.createContext("/", CampusComplaintSystem::serveWebsite);

        server.start();
        System.out.println("==================================================");
        System.out.println("  CampusCare Complaint System is LIVE!");
        System.out.println("  Binding Address : 0.0.0.0");
        System.out.println("  Listening Port  : " + port);
        System.out.println("  Storage File    : " + getComplaintFilePath());
        System.out.println("  Local URL       : http://localhost:" + port);
        System.out.println("==================================================");
        System.out.println("Press Ctrl+C to stop the server.");
    }

    @FunctionalInterface
    private interface ApiAction { void run(HttpExchange exchange, Map<String, String> data) throws Exception; }

    private static void handle(HttpExchange exchange, ApiAction action) throws IOException {
        try {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
                exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type, Authorization");
                exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
                exchange.sendResponseHeaders(204, -1);
                exchange.close();
                return;
            }
            Map<String, String> data = readParameters(exchange);
            action.run(exchange, data);
        } catch (Exception error) {
            sendJson(exchange, 500, "{\"error\":" + json(error.getMessage()) + "}");
        }
    }
}
