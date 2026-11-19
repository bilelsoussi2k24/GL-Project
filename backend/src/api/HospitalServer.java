package api;

import com.sun.net.httpserver.Filter;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import config.DatabaseConnection;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;

/** Local development API only. Replace DEMO_DOCTOR_ID with authenticated identity before deployment. */
public class HospitalServer {
    private static final int DEMO_DOCTOR_ID = 1;
    private static final Gson JSON = new GsonBuilder().serializeNulls().create();

    public static void main(String[] args) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 8080), 0);
        var context = server.createContext("/api/", HospitalServer::handle);
        context.getFilters().add(corsFilter());
        server.setExecutor(Executors.newFixedThreadPool(8));
        server.start();
        System.out.println("Hospital API running at http://localhost:8080");
    }

    private static void handle(HttpExchange ex) throws IOException {
        try {
            String path = ex.getRequestURI().getPath();
            String method = ex.getRequestMethod();
            if (method.equals("OPTIONS")) { send(ex, 204, null); return; }
            if (path.equals("/api/health")) {
                if (!method.equals("GET")) { error(ex, 405, "Method not allowed"); return; }
                send(ex, 200, Map.of("status", "ok")); return;
            }
            String[] p = path.split("/");
            // /api/doctors/me/patients[/id[/consultations]]
            if (p.length >= 5 && p[1].equals("api") && p[2].equals("doctors") && p[3].equals("me")) {
                if (p[4].equals("patients")) {
                    if (p.length == 5 && method.equals("GET")) { listPatients(ex); return; }
                    if (p.length == 6 && method.equals("GET")) { patient(ex, positiveId(p[5])); return; }
                    if (p.length == 7 && p[6].equals("consultations") && method.equals("GET")) {
                        consultationHistory(ex, positiveId(p[5])); return;
                    }
                }
                if (p[4].equals("appointments")) {
                    if (p.length == 5 && method.equals("GET")) { listAppointments(ex); return; }
                    if (p.length == 5 && method.equals("POST")) { createAppointment(ex); return; }
                    if (p.length == 7 && p[6].equals("status") && method.equals("PATCH")) {
                        updateAppointmentStatus(ex, positiveId(p[5])); return;
                    }
                }
            }
            if (p.length >= 3 && p[1].equals("api") && p[2].equals("consultations")) {
                if (p.length == 3 && method.equals("POST")) { createConsultation(ex); return; }
                if (p.length == 4 && method.equals("PUT")) { updateConsultation(ex, positiveId(p[3])); return; }
            }
            error(ex, 404, "Endpoint not found");
        } catch (IllegalArgumentException e) {
            error(ex, 400, e.getMessage());
        } catch (SQLException e) {
            e.printStackTrace();
            if ("23000".equals(e.getSQLState())) error(ex, 409, "Duplicate or invalid database reference");
            else error(ex, 500, "Database error");
        } catch (Exception e) {
            e.printStackTrace();
            error(ex, 500, "Internal server error");
        } finally {
            ex.close();
        }
    }

    private static void listPatients(HttpExchange ex) throws Exception {
        String sql = """
            SELECT p.id, p.first_name, p.last_name, p.birth_date, p.gender, p.phone, p.address
            FROM patients p JOIN doctor_patients dp ON dp.patient_id = p.id
            WHERE dp.doctor_id = ? ORDER BY p.id
            """;
        List<Map<String, Object>> results = new ArrayList<>();
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement st = con.prepareStatement(sql)) {
            st.setInt(1, DEMO_DOCTOR_ID);
            try (ResultSet rs = st.executeQuery()) {
                while (rs.next()) results.add(patientRow(rs));
            }
        }
        send(ex, 200, results);
    }

    private static void patient(HttpExchange ex, int patientId) throws Exception {
        String sql = """
            SELECT p.id, p.first_name, p.last_name, p.birth_date, p.gender, p.phone, p.address
            FROM patients p JOIN doctor_patients dp ON dp.patient_id = p.id
            WHERE dp.doctor_id = ? AND p.id = ?
            """;
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement st = con.prepareStatement(sql)) {
            st.setInt(1, DEMO_DOCTOR_ID); st.setInt(2, patientId);
            try (ResultSet rs = st.executeQuery()) {
                if (rs.next()) send(ex, 200, patientRow(rs));
                else error(ex, 404, "Patient not found");
            }
        }
    }

    private static Map<String, Object> patientRow(ResultSet rs) throws SQLException {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", rs.getInt("id"));
        m.put("firstName", rs.getString("first_name"));
        m.put("lastName", rs.getString("last_name"));
        Date birth = rs.getDate("birth_date");
        m.put("birthDate", birth == null ? null : birth.toLocalDate().toString());
        m.put("gender", rs.getString("gender"));
        m.put("phone", rs.getString("phone"));
        m.put("address", rs.getString("address"));
        return m;
    }

    private static void listAppointments(HttpExchange ex) throws Exception {
        String sql = """
            SELECT id, doctor_id, patient_id, appointment_date, status, reason
            FROM appointments WHERE doctor_id = ? ORDER BY appointment_date
            """;
        List<Map<String, Object>> results = new ArrayList<>();
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement st = con.prepareStatement(sql)) {
            st.setInt(1, DEMO_DOCTOR_ID);
            try (ResultSet rs = st.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", rs.getInt("id"));
                    m.put("doctorId", rs.getInt("doctor_id"));
                    m.put("patientId", rs.getInt("patient_id"));
                    m.put("appointmentDate", rs.getTimestamp("appointment_date").toLocalDateTime().toString());
                    m.put("status", rs.getString("status"));
                    m.put("reason", rs.getString("reason"));
                    results.add(m);
                }
            }
        }
        send(ex, 200, results);
    }

    private static void createAppointment(HttpExchange ex) throws Exception {
        JsonObject body = readBody(ex);
        int patientId = requiredInt(body, "patientId");
        String date = requiredString(body, "appointmentDate");
        LocalDateTime at;
        try { at = LocalDateTime.parse(date); }
        catch (Exception e) { throw new IllegalArgumentException("appointmentDate must be ISO format, e.g. 2026-11-25T10:00:00"); }
        String reason = optionalString(body, "reason");
        String sql = """
            INSERT INTO appointments (doctor_id, patient_id, appointment_date, status, reason)
            SELECT ?, dp.patient_id, ?, 'scheduled', ?
            FROM doctor_patients dp WHERE dp.doctor_id = ? AND dp.patient_id = ?
            """;
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement st = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            st.setInt(1, DEMO_DOCTOR_ID);
            st.setTimestamp(2, Timestamp.valueOf(at));
            st.setString(3, reason);
            st.setInt(4, DEMO_DOCTOR_ID); st.setInt(5, patientId);
            if (st.executeUpdate() == 0) { error(ex, 404, "Patient not assigned to doctor"); return; }
            try (ResultSet keys = st.getGeneratedKeys()) {
                if (keys.next()) send(ex, 201, Map.of("id", keys.getInt(1), "status", "scheduled"));
                else send(ex, 201, Map.of("created", true));
            }
        }
    }

    private static void updateAppointmentStatus(HttpExchange ex, int appointmentId) throws Exception {
        String status = requiredString(readBody(ex), "status");
        if (!List.of("scheduled", "completed", "cancelled").contains(status))
            throw new IllegalArgumentException("status must be scheduled, completed or cancelled");
        String sql = "UPDATE appointments SET status = ? WHERE id = ? AND doctor_id = ?";
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement st = con.prepareStatement(sql)) {
            st.setString(1, status); st.setInt(2, appointmentId); st.setInt(3, DEMO_DOCTOR_ID);
            if (st.executeUpdate() == 0) { error(ex, 404, "Appointment not found or unchanged"); return; }
            send(ex, 200, Map.of("updated", true));
        }
    }

    private static void consultationHistory(HttpExchange ex, int patientId) throws Exception {
        String sql = """
            SELECT c.id, c.appointment_id, c.diagnosis, c.treatment, c.notes, c.consultation_date
            FROM consultations c JOIN appointments a ON c.appointment_id = a.id
            WHERE a.patient_id = ? AND EXISTS (
                SELECT 1 FROM doctor_patients dp WHERE dp.patient_id = a.patient_id AND dp.doctor_id = ?
            ) ORDER BY c.consultation_date DESC
            """;
        List<Map<String, Object>> results = new ArrayList<>();
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement st = con.prepareStatement(sql)) {
            st.setInt(1, patientId); st.setInt(2, DEMO_DOCTOR_ID);
            try (ResultSet rs = st.executeQuery()) {
                while (rs.next()) results.add(consultationRow(rs));
            }
        }
        send(ex, 200, results);
    }

    private static Map<String, Object> consultationRow(ResultSet rs) throws SQLException {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", rs.getInt("id"));
        m.put("appointmentId", rs.getInt("appointment_id"));
        m.put("diagnosis", rs.getString("diagnosis"));
        m.put("treatment", rs.getString("treatment"));
        m.put("notes", rs.getString("notes"));
        Timestamp date = rs.getTimestamp("consultation_date");
        m.put("consultationDate", date == null ? null : date.toLocalDateTime().toString());
        return m;
    }

    private static void createConsultation(HttpExchange ex) throws Exception {
        JsonObject body = readBody(ex);
        int appointmentId = requiredInt(body, "appointmentId");
        String diagnosis = requiredString(body, "diagnosis");
        String treatment = optionalString(body, "treatment");
        String notes = optionalString(body, "notes");
        String sql = """
            INSERT INTO consultations (appointment_id, diagnosis, treatment, notes)
            SELECT a.id, ?, ?, ? FROM appointments a
            WHERE a.id = ? AND a.doctor_id = ? AND a.status = 'completed'
            """;
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement st = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            st.setString(1, diagnosis); st.setString(2, treatment); st.setString(3, notes);
            st.setInt(4, appointmentId); st.setInt(5, DEMO_DOCTOR_ID);
            if (st.executeUpdate() == 0) { error(ex, 404, "Completed appointment not found for doctor"); return; }
            try (ResultSet keys = st.getGeneratedKeys()) {
                if (keys.next()) send(ex, 201, Map.of("id", keys.getInt(1)));
                else send(ex, 201, Map.of("created", true));
            }
        }
    }

    private static void updateConsultation(HttpExchange ex, int consultationId) throws Exception {
        JsonObject body = readBody(ex);
        String diagnosis = requiredString(body, "diagnosis");
        String treatment = optionalString(body, "treatment");
        String notes = optionalString(body, "notes");
        String sql = """
            UPDATE consultations c JOIN appointments a ON c.appointment_id = a.id
            SET c.diagnosis = ?, c.treatment = ?, c.notes = ?
            WHERE c.id = ? AND a.doctor_id = ?
            """;
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement st = con.prepareStatement(sql)) {
            st.setString(1, diagnosis); st.setString(2, treatment); st.setString(3, notes);
            st.setInt(4, consultationId); st.setInt(5, DEMO_DOCTOR_ID);
            if (st.executeUpdate() == 0) { error(ex, 404, "Consultation not found or unchanged"); return; }
            send(ex, 200, Map.of("updated", true));
        }
    }

    private static JsonObject readBody(HttpExchange ex) throws IOException {
        String raw = new String(ex.getRequestBody().readNBytes(65537), StandardCharsets.UTF_8);
        if (raw.length() > 65536) throw new IllegalArgumentException("JSON body too large");
        try { return JsonParser.parseString(raw).getAsJsonObject(); }
        catch (Exception e) { throw new IllegalArgumentException("Invalid JSON body"); }
    }

    private static String requiredString(JsonObject obj, String key) {
        String s = optionalString(obj, key);
        if (s == null || s.isBlank()) throw new IllegalArgumentException("Missing field: " + key);
        return s;
    }

    private static String optionalString(JsonObject obj, String key) {
        if (!obj.has(key) || obj.get(key).isJsonNull()) return null;
        if (!obj.get(key).isJsonPrimitive() || !obj.get(key).getAsJsonPrimitive().isString())
            throw new IllegalArgumentException("Field must be a string: " + key);
        return obj.get(key).getAsString();
    }

    private static int requiredInt(JsonObject obj, String key) {
        try {
            if (!obj.has(key) || !obj.get(key).getAsJsonPrimitive().isNumber()) throw new Exception();
            int id = obj.get(key).getAsInt();
            if (id <= 0) throw new Exception();
            return id;
        } catch (Exception e) { throw new IllegalArgumentException("Field must be a positive integer: " + key); }
    }

    private static int positiveId(String raw) {
        try { int id = Integer.parseInt(raw); if (id > 0) return id; }
        catch (NumberFormatException ignored) {}
        throw new IllegalArgumentException("Invalid ID");
    }

    private static void error(HttpExchange ex, int code, String message) throws IOException {
        send(ex, code, Map.of("error", message));
    }

    private static void send(HttpExchange ex, int code, Object data) throws IOException {
        ex.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        ex.getResponseHeaders().set("Cache-Control", "no-store");
        if (code == 204) { ex.sendResponseHeaders(204, -1); return; }
        byte[] bytes = JSON.toJson(data).getBytes(StandardCharsets.UTF_8);
        ex.sendResponseHeaders(code, bytes.length);
        try (var out = ex.getResponseBody()) { out.write(bytes); }
    }



    private static Filter corsFilter() {
        return new Filter() {
            @Override
            public String description() {
                return "CORS filter for React frontend";
            }

            @Override
            public void doFilter(HttpExchange exchange, Chain chain) throws IOException {
                var headers = exchange.getResponseHeaders();
                headers.set("Access-Control-Allow-Origin", "http://localhost:5173");
                headers.set("Access-Control-Allow-Methods", "GET, POST, PUT, PATCH, OPTIONS");
                headers.set("Access-Control-Allow-Headers", "Content-Type, Authorization");

                if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                    exchange.sendResponseHeaders(204, -1);
                    exchange.close();
                    return;
                }
                chain.doFilter(exchange);
            }
        };
    }
}
