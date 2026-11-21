package api;

import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import config.AppConfig;
import config.DatabaseConnection;
import dao.AppointmentDAO;
import dao.ConsultationDAO;
import dao.DoctorDAO;
import dao.PatientDAO;
import dao.SettingsDAO;
import dao.UserDAO;
import model.Appointment;
import model.Consultation;
import model.Doctor;
import model.Patient;
import model.User;
import security.AuthService;
import security.PasswordHasher;
import security.Session;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Haven Hospital HTTP API.
 *
 * Every endpoint answers JSON. Protected routes read the bearer token, load the
 * session and check the role before the handler runs; doctor routes derive the
 * doctor id from the session, never from the request.
 */
public final class HospitalServer {

    private static final UserDAO USERS = new UserDAO();
    private static final DoctorDAO DOCTORS = new DoctorDAO();
    private static final PatientDAO PATIENTS = new PatientDAO();
    private static final AppointmentDAO APPOINTMENTS = new AppointmentDAO();
    private static final ConsultationDAO CONSULTATIONS = new ConsultationDAO();
    private static final SettingsDAO SETTINGS = new SettingsDAO();

    private static final Map<String, List<Long>> ASSISTANT_CALLS = new ConcurrentHashMap<>();
    private static final int ASSISTANT_LIMIT_PER_MINUTE = 15;

    private static final String ANY = "AUTH";
    private static final String ADMIN = "ADMIN";
    private static final String DOCTOR = "DOCTOR";

    private HospitalServer() {
    }

    @FunctionalInterface
    private interface Handler {
        Object handle(HttpExchange ex, Matcher path, Session session) throws Exception;
    }

    private record Route(String method, Pattern pattern, String access, Handler handler) {
    }

    /** Starts the API. {@link Main} is the process entry point. */
    public static HttpServer start() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(AppConfig.bindAddress(), AppConfig.port()), 0);
        var context = server.createContext("/api/", HospitalServer::dispatch);
        context.getFilters().add(new CorsFilter());
        server.setExecutor(Executors.newFixedThreadPool(Math.max(4, Runtime.getRuntime().availableProcessors() * 2)));
        server.start();
        System.out.println("Haven Hospital API listening on http://" + AppConfig.bindAddress() + ":" + AppConfig.port());
        System.out.println("Allowed browser origins: " + String.join(", ", AppConfig.corsOrigins()));
        return server;
    }

    private static void dispatch(HttpExchange ex) throws IOException {
        try {
            String path = ex.getRequestURI().getPath();
            String method = ex.getRequestMethod();

            Route matchedPath = null;
            for (Route route : routes()) {
                Matcher matcher = route.pattern().matcher(path);
                if (!matcher.matches()) continue;
                matchedPath = route;
                if (!route.method().equalsIgnoreCase(method)) continue;

                Session session = null;
                if (route.access() != null) {
                    String token = Http.bearerToken(ex);
                    session = AuthService.authenticate(token);
                    if (session == null) throw ApiException.unauthorized("Your session has expired. Sign in again.");
                    if (ADMIN.equals(route.access()) && !session.isAdmin()) {
                        throw ApiException.forbidden("Administrator access is required.");
                    }
                    if (DOCTOR.equals(route.access()) && !session.isDoctor()) {
                        throw ApiException.forbidden("Doctor access is required.");
                    }
                }

                Object result = route.handler().handle(ex, matcher, session);
                if (result instanceof Http.Response response) {
                    Http.send(ex, response.status(), response.body());
                } else {
                    Http.send(ex, 200, result);
                }
                return;
            }

            if (matchedPath != null) throw new ApiException(405, "Method not allowed for this endpoint.");
            throw ApiException.notFound("Endpoint not found.");
        } catch (ApiException e) {
            Http.error(ex, e.getStatus(), e.getMessage());
        } catch (AuthService.AuthException e) {
            Http.error(ex, e.getStatus(), e.getMessage());
        } catch (java.sql.SQLIntegrityConstraintViolationException e) {
            Http.error(ex, 409, "That value is already used or refers to a missing record.");
        } catch (java.sql.SQLException e) {
            System.err.println("Database error on " + ex.getRequestURI() + ": " + e.getMessage());
            if (e.getSQLState() != null && e.getSQLState().startsWith("08")) {
                Http.error(ex, 503, "The database is unavailable. Check the backend configuration.");
            } else {
                Http.error(ex, 500, "A database error prevented this operation.");
            }
        } catch (Exception e) {
            System.err.println("Unexpected error on " + ex.getRequestURI() + ": " + e);
            e.printStackTrace();
            Http.error(ex, 500, "Internal server error.");
        } finally {
            ex.close();
        }
    }

    /* ------------------------------------------------------------------ routes */

    private static List<Route> routes() {
        List<Route> routes = new ArrayList<>();

        routes.add(new Route("GET", pattern("/api/health"), null, (ex, m, s) -> health()));
        routes.add(new Route("GET", pattern("/api/public/hospital"), null, (ex, m, s) -> settingsJson(SETTINGS.getAll())));
        routes.add(new Route("POST", pattern("/api/assistant/chat"), null, HospitalServer::assistantChat));

        routes.add(new Route("POST", pattern("/api/auth/login"), null, HospitalServer::login));
        routes.add(new Route("POST", pattern("/api/auth/logout"), ANY, HospitalServer::logout));
        routes.add(new Route("GET", pattern("/api/auth/me"), ANY, HospitalServer::me));
        routes.add(new Route("PATCH", pattern("/api/auth/account"), ANY, HospitalServer::updateOwnAccount));
        routes.add(new Route("POST", pattern("/api/auth/password"), ANY, HospitalServer::changePassword));

        // Doctor workspace
        routes.add(new Route("GET", pattern("/api/doctors/me"), DOCTOR, HospitalServer::doctorProfile));
        routes.add(new Route("PATCH", pattern("/api/doctors/me"), DOCTOR, HospitalServer::updateDoctorProfile));
        routes.add(new Route("GET", pattern("/api/doctors/me/patients"), DOCTOR, HospitalServer::doctorPatients));
        routes.add(new Route("GET", pattern("/api/doctors/me/patients/(\\d+)"), DOCTOR, HospitalServer::doctorPatient));
        routes.add(new Route("GET", pattern("/api/doctors/me/patients/(\\d+)/consultations"), DOCTOR,
            HospitalServer::patientConsultations));
        routes.add(new Route("GET", pattern("/api/doctors/me/appointments"), DOCTOR, HospitalServer::doctorAppointments));
        routes.add(new Route("POST", pattern("/api/doctors/me/appointments"), DOCTOR, HospitalServer::createAppointment));
        routes.add(new Route("PUT", pattern("/api/doctors/me/appointments/(\\d+)"), DOCTOR,
            HospitalServer::updateAppointment));
        routes.add(new Route("PATCH", pattern("/api/doctors/me/appointments/(\\d+)/status"), DOCTOR,
            HospitalServer::appointmentStatus));
        routes.add(new Route("GET", pattern("/api/doctors/me/consultations"), DOCTOR, HospitalServer::doctorConsultations));
        routes.add(new Route("POST", pattern("/api/doctors/me/consultations"), DOCTOR, HospitalServer::createConsultation));
        routes.add(new Route("PUT", pattern("/api/doctors/me/consultations/(\\d+)"), DOCTOR,
            HospitalServer::updateConsultation));

        // Administrator
        routes.add(new Route("GET", pattern("/api/admin/stats"), ADMIN, (ex, m, s) -> adminStats()));
        routes.add(new Route("GET", pattern("/api/admin/activity"), ADMIN, (ex, m, s) -> adminActivity()));
        routes.add(new Route("GET", pattern("/api/admin/doctors"), ADMIN, HospitalServer::adminDoctors));
        routes.add(new Route("POST", pattern("/api/admin/doctors"), ADMIN, HospitalServer::createDoctor));
        routes.add(new Route("GET", pattern("/api/admin/doctors/(\\d+)"), ADMIN, HospitalServer::adminDoctor));
        routes.add(new Route("PUT", pattern("/api/admin/doctors/(\\d+)"), ADMIN, HospitalServer::updateDoctor));
        routes.add(new Route("DELETE", pattern("/api/admin/doctors/(\\d+)"), ADMIN, HospitalServer::deactivateDoctor));
        routes.add(new Route("PATCH", pattern("/api/admin/doctors/(\\d+)/status"), ADMIN, HospitalServer::doctorStatus));
        routes.add(new Route("GET", pattern("/api/admin/users"), ADMIN, HospitalServer::adminUsers));
        routes.add(new Route("POST", pattern("/api/admin/users"), ADMIN, HospitalServer::createUser));
        routes.add(new Route("PATCH", pattern("/api/admin/users/(\\d+)/active"), ADMIN, HospitalServer::userActive));
        routes.add(new Route("POST", pattern("/api/admin/users/(\\d+)/password"), ADMIN, HospitalServer::resetPassword));
        routes.add(new Route("GET", pattern("/api/admin/patients"), ADMIN, HospitalServer::adminPatients));
        routes.add(new Route("POST", pattern("/api/admin/patients"), ADMIN, HospitalServer::createPatient));
        routes.add(new Route("PUT", pattern("/api/admin/patients/(\\d+)"), ADMIN, HospitalServer::updatePatient));
        routes.add(new Route("PUT", pattern("/api/admin/patients/(\\d+)/doctor"), ADMIN, HospitalServer::assignPatient));
        routes.add(new Route("GET", pattern("/api/admin/appointments"), ADMIN, HospitalServer::adminAppointments));
        routes.add(new Route("GET", pattern("/api/admin/consultations"), ADMIN, HospitalServer::adminConsultations));
        routes.add(new Route("GET", pattern("/api/admin/settings"), ADMIN,
            (ex, m, s) -> settingsJson(SETTINGS.getAll())));
        routes.add(new Route("PUT", pattern("/api/admin/settings"), ADMIN, HospitalServer::saveSettings));

        return routes;
    }

    private static Pattern pattern(String path) {
        return Pattern.compile(path);
    }

    /* ------------------------------------------------------------------- public */

    private static Object health() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", "ok");
        body.put("database", DatabaseConnection.status());
        body.put("time", LocalDateTime.now().toString());
        return body;
    }

    private static Object assistantChat(HttpExchange ex, Matcher path, Session session) throws Exception {
        String client = Http.clientKey(ex);
        if (!allowAssistantCall(client)) {
            throw new ApiException(429, "Too many questions. Wait a minute and ask again.");
        }
        JsonObject body = Http.body(ex);
        String message = Http.requiredString(body, "message", "Your question", 1000);
        return AssistantClient.ask(message);
    }

    private static boolean allowAssistantCall(String client) {
        long now = System.currentTimeMillis();
        List<Long> calls = ASSISTANT_CALLS.computeIfAbsent(client, k -> new ArrayList<>());
        synchronized (calls) {
            calls.removeIf(time -> now - time > 60_000L);
            if (calls.size() >= ASSISTANT_LIMIT_PER_MINUTE) return false;
            calls.add(now);
            return true;
        }
    }

    /* --------------------------------------------------------------------- auth */

    private static Object login(HttpExchange ex, Matcher path, Session session) throws Exception {
        JsonObject body = Http.body(ex);
        String email = Http.email(body, "email", "Email", true);
        String password = Http.requiredString(body, "password", "Password", 200);
        String role = Http.optionalString(body, "role", "Role", 20);
        if (role != null) role = role.toUpperCase(Locale.ROOT);

        AuthService.LoginResult result = AuthService.login(email, password, role, Http.clientKey(ex));

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("token", result.token());
        response.put("expiresAt", result.expiresAt().toString());
        response.put("user", sessionUserJson(result.user(), result.doctorId()));
        return response;
    }

    private static Object logout(HttpExchange ex, Matcher path, Session session) throws Exception {
        AuthService.logout(Http.bearerToken(ex));
        return Http.Response.noContent();
    }

    private static Object me(HttpExchange ex, Matcher path, Session session) throws Exception {
        User user = USERS.findById(session.userId());
        if (user == null) throw ApiException.unauthorized("Your account no longer exists.");
        Doctor doctor = session.isDoctor() ? DOCTORS.findByUserId(user.getId()) : null;
        Map<String, Object> body = sessionUserJson(user, doctor == null ? null : Long.valueOf(doctor.getId()));
        if (doctor != null) body.put("profile", doctorJson(doctor, DOCTORS.countPatients(doctor.getId())));
        return body;
    }

    private static Object updateOwnAccount(HttpExchange ex, Matcher path, Session session) throws Exception {
        JsonObject body = Http.body(ex);
        String fullName = Http.optionalString(body, "fullName", "Full name", 120);
        String email = Http.email(body, "email", "Email", false);

        User user = USERS.findById(session.userId());
        if (user == null) throw ApiException.unauthorized("Your account no longer exists.");

        String newName = fullName == null ? user.getFullName() : fullName;
        if (newName.isBlank()) throw ApiException.badRequest("Full name is required.");
        if (email != null && !email.equalsIgnoreCase(user.getEmail())) {
            if (USERS.emailExists(email, (long) user.getId())) throw ApiException.conflict("That email is already in use.");
            USERS.updateEmail(user.getId(), email);
        }
        USERS.updateProfile(user.getId(), newName, user.getPhone());

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("fullName", newName);
        response.put("email", email == null ? user.getEmail() : email);
        return response;
    }

    private static Object changePassword(HttpExchange ex, Matcher path, Session session) throws Exception {
        JsonObject body = Http.body(ex);
        String current = Http.requiredString(body, "currentPassword", "Current password", 200);
        String next = Http.requiredString(body, "newPassword", "New password", 200);
        LocalDateTime changedAt = AuthService.changePassword(session, current, next);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("passwordChangedAt", System.currentTimeMillis());
        response.put("changedAt", changedAt.toString());
        return response;
    }

    private static Map<String, Object> sessionUserJson(User user, Long doctorId) {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("id", user.getId());
        json.put("email", user.getEmail());
        json.put("role", user.getRole());
        json.put("fullName", user.getFullName());
        json.put("doctorId", doctorId);
        json.put("passwordChangedAt", user.getPasswordChangedAt() == null
            ? null : user.getPasswordChangedAt().toInstant(java.time.ZoneOffset.UTC).toEpochMilli());
        return json;
    }

    /* ------------------------------------------------------------------- doctor */

    private static Object doctorProfile(HttpExchange ex, Matcher path, Session session) throws Exception {
        Doctor doctor = requireDoctor(session);
        Map<String, Object> json = doctorJson(doctor, DOCTORS.countPatients(doctor.getId()));
        json.put("passwordChangedAt", passwordChangedAt(session.userId()));
        return json;
    }

    private static Object updateDoctorProfile(HttpExchange ex, Matcher path, Session session) throws Exception {
        JsonObject body = Http.body(ex);
        Doctor doctor = requireDoctor(session);

        String firstName = Http.optionalString(body, "firstName", "First name", 50);
        String lastName = Http.optionalString(body, "lastName", "Last name", 50);
        String name = Http.optionalString(body, "name", "Full name", 120);
        if (name != null && (firstName == null || lastName == null)) {
            String cleaned = name.replaceFirst("(?i)^dr\\.?\\s*", "").strip();
            int split = cleaned.lastIndexOf(' ');
            if (split <= 0) throw ApiException.badRequest("Enter both a first and a last name.");
            firstName = cleaned.substring(0, split).strip();
            lastName = cleaned.substring(split + 1).strip();
        }
        if (firstName == null) firstName = doctor.getFirstName();
        if (lastName == null) lastName = doctor.getLastName();
        if (firstName.isBlank() || lastName.isBlank()) throw ApiException.badRequest("Enter both a first and a last name.");

        String phone = body.has("phone") ? Http.optionalString(body, "phone", "Phone", 20) : doctor.getPhone();
        String office = body.has("office") ? Http.optionalString(body, "office", "Office", 80) : doctor.getOffice();
        String email = Http.email(body, "email", "Email", false);

        String workDays = Http.workDays(body, "workDays", "Working days");
        if (workDays == null) workDays = doctor.getWorkDays();

        LocalTime first = body.has("firstAppointment")
            ? Http.time(body, "firstAppointment", "First appointment") : LocalTime.parse(doctor.getFirstAppointment());
        LocalTime last = body.has("lastAppointment")
            ? Http.time(body, "lastAppointment", "Last appointment") : LocalTime.parse(doctor.getLastAppointment());
        if (!last.isAfter(first)) throw ApiException.badRequest("The last appointment must be after the first one.");

        Integer visitLength = Http.optionalInt(body, "visitLength", "Visit length", 5, 180);
        int length = visitLength == null ? doctor.getVisitLength() : visitLength;

        DOCTORS.updateOwnProfile(session.userId(), firstName, lastName, phone, office, workDays,
            formatTime(first), formatTime(last), length);
        USERS.updateProfile(session.userId(), firstName + " " + lastName, phone);
        if (email != null && !email.equalsIgnoreCase(doctor.getEmail())) {
            if (USERS.emailExists(email, session.userId())) throw ApiException.conflict("That email is already in use.");
            USERS.updateEmail(session.userId(), email);
        }

        Doctor updated = requireDoctor(session);
        return doctorJson(updated, DOCTORS.countPatients(updated.getId()));
    }

    private static Object doctorPatients(HttpExchange ex, Matcher path, Session session) throws Exception {
        Doctor doctor = requireDoctor(session);
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Patient patient : PATIENTS.getPatientsByDoctor(doctor.getId(), Http.queryValue(ex, "q"))) {
            rows.add(patientJson(patient));
        }
        return rows;
    }

    private static Object doctorPatient(HttpExchange ex, Matcher path, Session session) throws Exception {
        Doctor doctor = requireDoctor(session);
        long patientId = Long.parseLong(Http.pathParam(path, 1));
        Patient patient = PATIENTS.getPatientById(doctor.getId(), patientId);
        if (patient == null) throw ApiException.notFound("Patient not found.");
        Map<String, Object> json = patientJson(patient);
        json.put("consultations", CONSULTATIONS.getConsultationHistory(doctor.getId(), patientId).size());
        return json;
    }

    private static Object patientConsultations(HttpExchange ex, Matcher path, Session session) throws Exception {
        Doctor doctor = requireDoctor(session);
        long patientId = Long.parseLong(Http.pathParam(path, 1));
        if (PATIENTS.getPatientById(doctor.getId(), patientId) == null) {
            throw ApiException.notFound("Patient not found.");
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Consultation consultation : CONSULTATIONS.getConsultationHistory(doctor.getId(), patientId)) {
            rows.add(consultationJson(consultation));
        }
        return rows;
    }

    private static Object doctorAppointments(HttpExchange ex, Matcher path, Session session) throws Exception {
        Doctor doctor = requireDoctor(session);
        LocalDate from = optionalDate(ex, "from");
        LocalDate to = optionalDate(ex, "to");
        String status = Http.queryValue(ex, "status");
        if (status != null) {
            status = Http.oneOf(status, "status", List.of("scheduled", "completed", "cancelled", "missed"));
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Appointment appointment : APPOINTMENTS.getAppointmentsByDoctor(doctor.getId(), from, to, status)) {
            rows.add(appointmentJson(appointment));
        }
        return rows;
    }

    private static Object createAppointment(HttpExchange ex, Matcher path, Session session) throws Exception {
        JsonObject body = Http.body(ex);
        Doctor doctor = requireDoctor(session);
        long patientId = Http.requiredId(body, "patientId", "Patient");
        LocalDateTime date = Http.dateTime(body, "appointmentDate", "Appointment date");
        String reason = Http.optionalString(body, "reason", "Reason", 255);

        if (date.getYear() < 2000 || date.getYear() > 2100) {
            throw ApiException.badRequest("Choose a realistic appointment date.");
        }

        long id = APPOINTMENTS.create(doctor.getId(), patientId, date, reason);
        if (id < 0) throw ApiException.badRequest("That patient is not assigned to you.");

        Appointment created = APPOINTMENTS.find(doctor.getId(), id);
        return new Http.Response(201, appointmentJson(created));
    }

    private static Object updateAppointment(HttpExchange ex, Matcher path, Session session) throws Exception {
        JsonObject body = Http.body(ex);
        Doctor doctor = requireDoctor(session);
        long id = Long.parseLong(Http.pathParam(path, 1));
        if (APPOINTMENTS.find(doctor.getId(), id) == null) throw ApiException.notFound("Appointment not found.");

        long patientId = Http.requiredId(body, "patientId", "Patient");
        LocalDateTime date = Http.dateTime(body, "appointmentDate", "Appointment date");
        String reason = Http.optionalString(body, "reason", "Reason", 255);

        boolean updated = APPOINTMENTS.update(doctor.getId(), id, patientId, date, reason);
        if (!updated) throw ApiException.badRequest("That patient is not assigned to you.");
        return appointmentJson(APPOINTMENTS.find(doctor.getId(), id));
    }

    private static Object appointmentStatus(HttpExchange ex, Matcher path, Session session) throws Exception {
        JsonObject body = Http.body(ex);
        Doctor doctor = requireDoctor(session);
        long id = Long.parseLong(Http.pathParam(path, 1));
        String status = Http.oneOf(Http.requiredString(body, "status", "Status", 20), "Status",
            List.of("scheduled", "completed", "cancelled", "missed"));

        if (!APPOINTMENTS.updateStatus(doctor.getId(), id, status)) {
            throw ApiException.notFound("Appointment not found.");
        }
        return appointmentJson(APPOINTMENTS.find(doctor.getId(), id));
    }

    private static Object doctorConsultations(HttpExchange ex, Matcher path, Session session) throws Exception {
        Doctor doctor = requireDoctor(session);
        int limit = parseLimit(Http.queryValue(ex, "limit"), 100);
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Consultation consultation : CONSULTATIONS.listByDoctor(doctor.getId(), limit)) {
            rows.add(consultationJson(consultation));
        }
        return rows;
    }

    private static Object createConsultation(HttpExchange ex, Matcher path, Session session) throws Exception {
        JsonObject body = Http.body(ex);
        Doctor doctor = requireDoctor(session);
        long appointmentId = Http.requiredId(body, "appointmentId", "Appointment");
        String diagnosis = Http.requiredString(body, "diagnosis", "Diagnosis", 2000);
        String treatment = Http.optionalString(body, "treatment", "Treatment", 2000);
        String notes = Http.optionalString(body, "notes", "Notes", 4000);

        if (CONSULTATIONS.existsForAppointment(appointmentId)) {
            throw ApiException.conflict("This appointment already has a consultation. Edit it instead.");
        }
        long id = CONSULTATIONS.create(doctor.getId(), appointmentId, diagnosis, treatment, notes);
        if (id < 0) {
            throw ApiException.badRequest("Only a completed appointment of yours can be recorded.");
        }
        return new Http.Response(201, consultationJson(CONSULTATIONS.find(doctor.getId(), id)));
    }

    private static Object updateConsultation(HttpExchange ex, Matcher path, Session session) throws Exception {
        JsonObject body = Http.body(ex);
        Doctor doctor = requireDoctor(session);
        long id = Long.parseLong(Http.pathParam(path, 1));
        if (CONSULTATIONS.find(doctor.getId(), id) == null) throw ApiException.notFound("Consultation not found.");

        String diagnosis = Http.requiredString(body, "diagnosis", "Diagnosis", 2000);
        String treatment = Http.optionalString(body, "treatment", "Treatment", 2000);
        String notes = Http.optionalString(body, "notes", "Notes", 4000);

        CONSULTATIONS.update(doctor.getId(), id, diagnosis, treatment, notes);
        return consultationJson(CONSULTATIONS.find(doctor.getId(), id));
    }

    private static Doctor requireDoctor(Session session) throws Exception {
        Doctor doctor = DOCTORS.findByUserId(session.userId());
        if (doctor == null) throw ApiException.forbidden("No doctor profile is linked to this account.");
        if ("inactive".equals(doctor.getStatus())) {
            throw ApiException.forbidden("This doctor profile is deactivated.");
        }
        return doctor;
    }

    private static Long passwordChangedAt(long userId) throws Exception {
        User user = USERS.findById(userId);
        if (user == null || user.getPasswordChangedAt() == null) return null;
        return user.getPasswordChangedAt().toInstant(java.time.ZoneOffset.UTC).toEpochMilli();
    }

    /* -------------------------------------------------------------- administrator */

    private static Object adminStats() throws Exception {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("doctors", DOCTORS.count());
        stats.put("activeDoctors", DOCTORS.countByStatus("active"));
        stats.put("doctorsOnLeave", DOCTORS.countByStatus("leave"));
        stats.put("inactiveDoctors", DOCTORS.countByStatus("inactive"));
        stats.put("patients", PATIENTS.count());
        stats.put("appointments", APPOINTMENTS.countAll());
        stats.put("appointmentsToday", APPOINTMENTS.countToday());
        stats.put("upcomingAppointments", APPOINTMENTS.countUpcoming());
        stats.put("completedAppointments", APPOINTMENTS.countByStatus("completed"));
        stats.put("consultations", CONSULTATIONS.countAll());
        stats.put("doctorAccounts", USERS.countByRole("DOCTOR"));
        stats.put("adminAccounts", USERS.countByRole("ADMIN"));
        return stats;
    }

    private static Object adminActivity() throws Exception {
        List<Map<String, Object>> events = new ArrayList<>();
        for (Map<String, Object> appointment : APPOINTMENTS.listRecent(6)) {
            Map<String, Object> event = new LinkedHashMap<>();
            event.put("type", "appointment");
            event.put("title", appointment.get("patientName") + " · " + appointment.get("status"));
            event.put("detail", appointment.get("doctorName") + " · " + appointment.get("reason"));
            event.put("at", appointment.get("appointmentDate"));
            events.add(event);
        }
        for (Map<String, Object> consultation : CONSULTATIONS.listRecentWithNames(6)) {
            Map<String, Object> event = new LinkedHashMap<>();
            event.put("type", "consultation");
            event.put("title", "Consultation for " + consultation.get("patientName"));
            event.put("detail", consultation.get("doctorName") + " · " + consultation.get("diagnosis"));
            event.put("at", consultation.get("consultationDate"));
            events.add(event);
        }
        for (Map<String, Object> account : USERS.listRecent(3)) {
            Map<String, Object> event = new LinkedHashMap<>();
            event.put("type", "account");
            event.put("title", "Account created · " + account.get("email"));
            event.put("detail", account.get("role") + (account.get("fullName") == null ? "" : " · " + account.get("fullName")));
            event.put("at", account.get("createdAt"));
            events.add(event);
        }
        events.sort(Comparator.comparing((Map<String, Object> e) -> String.valueOf(e.get("at"))).reversed());
        return events.size() > 10 ? events.subList(0, 10) : events;
    }

    private static Object adminDoctors(HttpExchange ex, Matcher path, Session session) throws Exception {
        String status = Http.queryValue(ex, "status");
        if (status != null) {
            status = Http.oneOf(status, "status", List.of("active", "leave", "inactive"));
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Doctor doctor : DOCTORS.findAll(Http.queryValue(ex, "q"), status)) {
            rows.add(doctorJson(doctor, DOCTORS.countPatients(doctor.getId())));
        }
        return rows;
    }

    private static Object adminDoctor(HttpExchange ex, Matcher path, Session session) throws Exception {
        long id = Long.parseLong(Http.pathParam(path, 1));
        Doctor doctor = DOCTORS.findById(id);
        if (doctor == null) throw ApiException.notFound("Doctor not found.");
        Map<String, Object> json = doctorJson(doctor, DOCTORS.countPatients(doctor.getId()));
        List<String> patientNames = new ArrayList<>();
        for (Patient patient : PATIENTS.getPatientsByDoctor(id, null)) patientNames.add(patient.getFullName());
        json.put("patientNames", patientNames);
        return json;
    }

    private static Object createDoctor(HttpExchange ex, Matcher path, Session session) throws Exception {
        JsonObject body = Http.body(ex);
        String firstName = Http.requiredString(body, "firstName", "First name", 50);
        String lastName = Http.requiredString(body, "lastName", "Last name", 50);
        String specialty = Http.requiredString(body, "specialty", "Specialty", 100);
        String email = Http.email(body, "email", "Email", true);
        String password = Http.requiredString(body, "password", "Password", 200);
        String phone = Http.optionalString(body, "phone", "Phone", 20);
        String office = Http.optionalString(body, "office", "Office", 80);
        String status = Http.oneOf(
            orElse(Http.optionalString(body, "status", "Status", 20), "active"),
            "Status", List.of("active", "leave", "inactive"));

        if (!PasswordHasher.isStrongEnough(password)) {
            throw ApiException.badRequest("Use a password of at least 8 characters with a letter and a number.");
        }
        if (office != null && office.length() > 80) throw ApiException.badRequest("Office is too long.");
        if (USERS.emailExists(email, null)) throw ApiException.conflict("An account with that email already exists.");

        long id = DOCTORS.createWithAccount(email, PasswordHasher.hash(password), firstName + " " + lastName,
            phone, firstName, lastName, specialty, office, status);
        if ("inactive".equals(status)) {
            Doctor created = DOCTORS.findById(id);
            USERS.setActive(created.getUserId(), false);
        }
        return new Http.Response(201, doctorJson(DOCTORS.findById(id), 0));
    }

    private static Object updateDoctor(HttpExchange ex, Matcher path, Session session) throws Exception {
        JsonObject body = Http.body(ex);
        long id = Long.parseLong(Http.pathParam(path, 1));
        Doctor doctor = DOCTORS.findById(id);
        if (doctor == null) throw ApiException.notFound("Doctor not found.");

        String firstName = orElse(Http.optionalString(body, "firstName", "First name", 50), doctor.getFirstName());
        String lastName = orElse(Http.optionalString(body, "lastName", "Last name", 50), doctor.getLastName());
        String specialty = orElse(Http.optionalString(body, "specialty", "Specialty", 100), doctor.getSpecialty());
        String phone = body.has("phone") ? Http.optionalString(body, "phone", "Phone", 20) : doctor.getPhone();
        String office = body.has("office") ? Http.optionalString(body, "office", "Office", 80) : doctor.getOffice();
        String status = body.has("status")
            ? Http.oneOf(body.get("status").getAsString(), "Status", List.of("active", "leave", "inactive"))
            : doctor.getStatus();
        String email = Http.email(body, "email", "Email", false);

        DOCTORS.update(id, firstName, lastName, specialty, phone, office, status);
        USERS.updateProfile(doctor.getUserId(), firstName + " " + lastName, phone);
        if (email != null && !email.equalsIgnoreCase(doctor.getEmail())) {
            if (USERS.emailExists(email, (long) doctor.getUserId())) {
                throw ApiException.conflict("Another account already uses that email.");
            }
            USERS.updateEmail(doctor.getUserId(), email);
        }
        USERS.setActive(doctor.getUserId(), !"inactive".equals(status));

        return doctorJson(DOCTORS.findById(id), DOCTORS.countPatients(id));
    }

    private static Object deactivateDoctor(HttpExchange ex, Matcher path, Session session) throws Exception {
        long id = Long.parseLong(Http.pathParam(path, 1));
        Doctor doctor = DOCTORS.findById(id);
        if (doctor == null) throw ApiException.notFound("Doctor not found.");

        DOCTORS.updateStatus(id, "inactive");
        USERS.setActive(doctor.getUserId(), false);
        new dao.SessionDAO().deleteForUser(doctor.getUserId(), null);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("deactivated", true);
        response.put("message", doctor.getFullName() + " can no longer sign in. Their records are kept.");
        response.put("doctor", doctorJson(DOCTORS.findById(id), DOCTORS.countPatients(id)));
        return response;
    }

    private static Object doctorStatus(HttpExchange ex, Matcher path, Session session) throws Exception {
        JsonObject body = Http.body(ex);
        long id = Long.parseLong(Http.pathParam(path, 1));
        Doctor doctor = DOCTORS.findById(id);
        if (doctor == null) throw ApiException.notFound("Doctor not found.");
        String status = Http.oneOf(Http.requiredString(body, "status", "Status", 20), "Status",
            List.of("active", "leave", "inactive"));

        DOCTORS.updateStatus(id, status);
        USERS.setActive(doctor.getUserId(), !"inactive".equals(status));
        return doctorJson(DOCTORS.findById(id), DOCTORS.countPatients(id));
    }

    private static Object adminUsers(HttpExchange ex, Matcher path, Session session) throws Exception {
        String role = Http.queryValue(ex, "role");
        if (role != null) role = Http.oneOf(role.toUpperCase(Locale.ROOT), "role", List.of("ADMIN", "DOCTOR"));
        return USERS.list(role, Http.queryValue(ex, "q"));
    }

    private static Object createUser(HttpExchange ex, Matcher path, Session session) throws Exception {
        JsonObject body = Http.body(ex);
        String email = Http.email(body, "email", "Email", true);
        String password = Http.requiredString(body, "password", "Password", 200);
        String role = Http.oneOf(Http.requiredString(body, "role", "Role", 20).toUpperCase(Locale.ROOT),
            "Role", List.of("ADMIN", "DOCTOR"));
        String fullName = Http.requiredString(body, "fullName", "Full name", 120);
        String phone = Http.optionalString(body, "phone", "Phone", 20);

        if (!PasswordHasher.isStrongEnough(password)) {
            throw ApiException.badRequest("Use a password of at least 8 characters with a letter and a number.");
        }
        if (USERS.emailExists(email, null)) throw ApiException.conflict("An account with that email already exists.");

        if ("DOCTOR".equals(role)) {
            String specialty = Http.requiredString(body, "specialty", "Specialty", 100);
            String office = Http.optionalString(body, "office", "Office", 80);
            String cleaned = fullName.replaceFirst("(?i)^dr\\.?\\s*", "").strip();
            int split = cleaned.lastIndexOf(' ');
            String firstName = split <= 0 ? cleaned : cleaned.substring(0, split).strip();
            String lastName = split <= 0 ? "Doctor" : cleaned.substring(split + 1).strip();
            long doctorId = DOCTORS.createWithAccount(email, PasswordHasher.hash(password), fullName, phone,
                firstName, lastName, specialty, office, "active");
            return new Http.Response(201, doctorJson(DOCTORS.findById(doctorId), 0));
        }

        long userId = USERS.create(email, PasswordHasher.hash(password), role, fullName, phone);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("id", userId);
        response.put("email", email);
        response.put("role", role);
        response.put("fullName", fullName);
        response.put("active", true);
        return new Http.Response(201, response);
    }

    private static Object userActive(HttpExchange ex, Matcher path, Session session) throws Exception {
        JsonObject body = Http.body(ex);
        long userId = Long.parseLong(Http.pathParam(path, 1));
        if (!body.has("active") || !body.get("active").isJsonPrimitive()) {
            throw ApiException.badRequest("active must be true or false.");
        }
        boolean active = body.get("active").getAsBoolean();

        User user = USERS.findById(userId);
        if (user == null) throw ApiException.notFound("Account not found.");
        if (user.getId() == session.userId() && !active) {
            throw ApiException.badRequest("You cannot deactivate your own account.");
        }
        if (!active && "ADMIN".equals(user.getRole()) && USERS.countActiveAdmins() <= 1) {
            throw ApiException.badRequest("At least one active administrator is required.");
        }

        USERS.setActive(userId, active);
        if (!active) new dao.SessionDAO().deleteForUser(userId, null);
        if ("DOCTOR".equals(user.getRole())) {
            Doctor doctor = DOCTORS.findByUserId(userId);
            if (doctor != null && "inactive".equals(doctor.getStatus()) && active) {
                DOCTORS.updateStatus(doctor.getId(), "active");
            }
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("id", userId);
        response.put("active", active);
        return response;
    }

    private static Object resetPassword(HttpExchange ex, Matcher path, Session session) throws Exception {
        JsonObject body = Http.body(ex);
        long userId = Long.parseLong(Http.pathParam(path, 1));
        String password = Http.requiredString(body, "password", "New password", 200);
        if (!PasswordHasher.isStrongEnough(password)) {
            throw ApiException.badRequest("Use a password of at least 8 characters with a letter and a number.");
        }
        User user = USERS.findById(userId);
        if (user == null) throw ApiException.notFound("Account not found.");

        USERS.updatePassword(userId, PasswordHasher.hash(password));
        new dao.SessionDAO().deleteForUser(userId, null);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("id", userId);
        response.put("passwordReset", true);
        return response;
    }

    private static Object adminPatients(HttpExchange ex, Matcher path, Session session) throws Exception {
        return PATIENTS.listAll(Http.queryValue(ex, "q"));
    }

    private static Object createPatient(HttpExchange ex, Matcher path, Session session) throws Exception {
        JsonObject body = Http.body(ex);
        String firstName = Http.requiredString(body, "firstName", "First name", 50);
        String lastName = Http.requiredString(body, "lastName", "Last name", 50);
        LocalDate birthDate = Http.date(body, "birthDate", "Birth date");
        String gender = Http.optionalString(body, "gender", "Gender", 20);
        String phone = Http.optionalString(body, "phone", "Phone", 20);
        String address = Http.optionalString(body, "address", "Address", 255);

        if (birthDate.isAfter(LocalDate.now())) throw ApiException.badRequest("Birth date cannot be in the future.");

        long id = PATIENTS.create(firstName, lastName, birthDate, gender, phone, address);
        Long doctorId = body.has("doctorId") ? Http.requiredId(body, "doctorId", "Doctor") : null;
        if (doctorId != null) {
            if (DOCTORS.findById(doctorId) == null) throw ApiException.notFound("Doctor not found.");
            PATIENTS.assignDoctor(id, doctorId);
        }
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("id", id);
        response.put("hhId", String.format("HH-%06d", id));
        return new Http.Response(201, response);
    }

    private static Object updatePatient(HttpExchange ex, Matcher path, Session session) throws Exception {
        JsonObject body = Http.body(ex);
        long id = Long.parseLong(Http.pathParam(path, 1));
        Patient patient = PATIENTS.findById(id);
        if (patient == null) throw ApiException.notFound("Patient not found.");

        String firstName = orElse(Http.optionalString(body, "firstName", "First name", 50), patient.getFirstName());
        String lastName = orElse(Http.optionalString(body, "lastName", "Last name", 50), patient.getLastName());
        LocalDate birthDate = body.has("birthDate")
            ? Http.date(body, "birthDate", "Birth date") : patient.getBirthDate();
        String gender = body.has("gender") ? Http.optionalString(body, "gender", "Gender", 20) : patient.getGender();
        String phone = body.has("phone") ? Http.optionalString(body, "phone", "Phone", 20) : patient.getPhone();
        String address = body.has("address") ? Http.optionalString(body, "address", "Address", 255) : patient.getAddress();

        PATIENTS.update(id, firstName, lastName, birthDate, gender, phone, address);
        return patientJson(PATIENTS.findById(id));
    }

    private static Object assignPatient(HttpExchange ex, Matcher path, Session session) throws Exception {
        JsonObject body = Http.body(ex);
        long patientId = Long.parseLong(Http.pathParam(path, 1));
        long doctorId = Http.requiredId(body, "doctorId", "Doctor");
        if (PATIENTS.findById(patientId) == null) throw ApiException.notFound("Patient not found.");
        Doctor doctor = DOCTORS.findById(doctorId);
        if (doctor == null) throw ApiException.notFound("Doctor not found.");
        if ("inactive".equals(doctor.getStatus())) {
            throw ApiException.badRequest("Assign patients to an active doctor.");
        }
        PATIENTS.assignDoctor(patientId, doctorId);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("assigned", true);
        response.put("doctorId", doctorId);
        response.put("patientId", patientId);
        return response;
    }

    private static Object adminAppointments(HttpExchange ex, Matcher path, Session session) throws Exception {
        return APPOINTMENTS.listRecent(parseLimit(Http.queryValue(ex, "limit"), 50));
    }

    private static Object adminConsultations(HttpExchange ex, Matcher path, Session session) throws Exception {
        return CONSULTATIONS.listRecentWithNames(parseLimit(Http.queryValue(ex, "limit"), 50));
    }

    private static Object saveSettings(HttpExchange ex, Matcher path, Session session) throws Exception {
        JsonObject body = Http.body(ex);
        Map<String, String> values = new LinkedHashMap<>();

        values.put("name", Http.requiredString(body, "name", "Hospital name", 120));
        values.put("phone", orElse(Http.optionalString(body, "phone", "Reception phone", 30), ""));
        values.put("erNumber", orElse(Http.optionalString(body, "erNumber", "Emergency number", 30), ""));
        values.put("street", orElse(Http.optionalString(body, "street", "Street address", 160), ""));
        values.put("cityState", orElse(Http.optionalString(body, "cityState", "City and state", 120), ""));
        values.put("parking", orElse(Http.optionalString(body, "parking", "Parking details", 200), ""));

        String email = Http.optionalString(body, "email", "Email", 150);
        if (email != null && !email.startsWith("[") && !Http.isEmail(email)) {
            throw ApiException.badRequest("Enter a valid email address.");
        }
        values.put("email", email == null ? "" : email);

        JsonObject visiting = body.has("visiting") && body.get("visiting").isJsonObject()
            ? body.getAsJsonObject("visiting") : new JsonObject();
        String start = null;
        String end = null;
        if (visiting.has("start") && !visiting.get("start").isJsonNull()) {
            start = formatTime(Http.parseTime(visiting.get("start").getAsString(), "Opening time"));
        }
        if (visiting.has("end") && !visiting.get("end").isJsonNull()) {
            end = formatTime(Http.parseTime(visiting.get("end").getAsString(), "Closing time"));
        }
        if ((start == null) != (end == null)) {
            throw ApiException.badRequest("Set both visiting times, or neither.");
        }
        if (start != null && !LocalTime.parse(end).isAfter(LocalTime.parse(start))) {
            throw ApiException.badRequest("Closing time must be after opening time.");
        }
        values.put("visitingStart", start == null ? "" : start);
        values.put("visitingEnd", end == null ? "" : end);

        Integer maxVisitors = null;
        if (visiting.has("maxVisitors") && !visiting.get("maxVisitors").isJsonNull()) {
            maxVisitors = Http.optionalInt(visiting, "maxVisitors", "Visitors per patient", 1, 10);
        }
        values.put("maxVisitors", String.valueOf(maxVisitors == null ? 2 : maxVisitors));

        SETTINGS.putAll(values);
        return settingsJson(SETTINGS.getAll());
    }

    /* --------------------------------------------------------------- JSON shapes */

    private static Map<String, Object> settingsJson(Map<String, String> values) {
        Map<String, Object> visiting = new LinkedHashMap<>();
        String start = blankToNull(values.get("visitingStart"));
        String end = blankToNull(values.get("visitingEnd"));
        visiting.put("start", start);
        visiting.put("end", end);
        visiting.put("maxVisitors", safeInt(values.get("maxVisitors"), 2));
        visiting.put("label", start != null && end != null
            ? "Mon–Sun, " + start + " to " + end
            : "Mon–Sun, [TIME] to [TIME]");

        Map<String, Object> json = new LinkedHashMap<>();
        json.put("name", orElse(values.get("name"), "Haven Hospital"));
        json.put("phone", orElse(values.get("phone"), ""));
        json.put("erNumber", orElse(values.get("erNumber"), ""));
        json.put("email", orElse(values.get("email"), ""));
        json.put("street", orElse(values.get("street"), ""));
        json.put("cityState", orElse(values.get("cityState"), ""));
        json.put("parking", orElse(values.get("parking"), ""));
        json.put("visiting", visiting);
        return json;
    }

    private static Map<String, Object> patientJson(Patient patient) {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("id", patient.getId());
        json.put("firstName", patient.getFirstName());
        json.put("lastName", patient.getLastName());
        json.put("name", patient.getFullName());
        json.put("hhId", String.format("HH-%06d", patient.getId()));
        json.put("birthDate", patient.getBirthDate() == null ? null : patient.getBirthDate().toString());
        json.put("gender", patient.getGender());
        json.put("phone", patient.getPhone());
        json.put("address", patient.getAddress());
        return json;
    }

    private static Map<String, Object> doctorJson(Doctor doctor, long patients) {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("id", doctor.getId());
        json.put("userId", doctor.getUserId());
        json.put("staffId", doctor.getStaffId());
        json.put("firstName", doctor.getFirstName());
        json.put("lastName", doctor.getLastName());
        json.put("name", doctor.getFullName());
        json.put("specialty", doctor.getSpecialty());
        json.put("phone", doctor.getPhone());
        json.put("email", doctor.getEmail());
        json.put("office", doctor.getOffice());
        json.put("status", doctor.getStatus());
        json.put("accountActive", doctor.isAccountActive());
        json.put("workDays", Http.parseWorkDays(doctor.getWorkDays()));
        json.put("firstAppointment", doctor.getFirstAppointment());
        json.put("lastAppointment", doctor.getLastAppointment());
        json.put("visitLength", doctor.getVisitLength());
        json.put("patients", patients);
        return json;
    }

    private static Map<String, Object> appointmentJson(Appointment appointment) {
        Map<String, Object> patient = new LinkedHashMap<>();
        patient.put("id", appointment.getPatientId());
        patient.put("name", (nullToEmpty(appointment.getPatientFirstName()) + " "
            + nullToEmpty(appointment.getPatientLastName())).strip());
        patient.put("hhId", String.format("HH-%06d", appointment.getPatientId()));
        patient.put("phone", appointment.getPatientPhone());

        Map<String, Object> json = new LinkedHashMap<>();
        json.put("id", appointment.getId());
        json.put("doctorId", appointment.getDoctorId());
        json.put("patientId", appointment.getPatientId());
        json.put("patient", patient);
        json.put("appointmentDate", appointment.getAppointmentDate().toString());
        json.put("status", appointment.getStatus());
        json.put("reason", appointment.getReason());
        return json;
    }

    private static Map<String, Object> consultationJson(Consultation consultation) {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("id", consultation.getId());
        json.put("appointmentId", consultation.getAppointmentId());
        json.put("patientId", consultation.getPatientId());
        json.put("patientName", consultation.getPatientName());
        json.put("diagnosis", consultation.getDiagnosis());
        json.put("treatment", consultation.getTreatment());
        json.put("notes", consultation.getNotes());
        json.put("consultationDate", consultation.getConsultationDate() == null
            ? null : consultation.getConsultationDate().toString());
        return json;
    }

    /* -------------------------------------------------------------------- helpers */

    private static LocalDate optionalDate(HttpExchange ex, String key) {
        String raw = Http.queryValue(ex, key);
        if (raw == null) return null;
        try {
            return LocalDate.parse(raw);
        } catch (RuntimeException e) {
            throw ApiException.badRequest(key + " must look like 2026-11-25.");
        }
    }

    private static int parseLimit(String raw, int fallback) {
        if (raw == null) return fallback;
        try {
            int limit = Integer.parseInt(raw);
            if (limit < 1) throw new NumberFormatException();
            return Math.min(limit, 200);
        } catch (NumberFormatException e) {
            throw ApiException.badRequest("limit must be a positive whole number.");
        }
    }

    private static String formatTime(LocalTime time) {
        return String.format("%02d:%02d", time.getHour(), time.getMinute());
    }

    private static String orElse(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static int safeInt(String value, int fallback) {
        try {
            return value == null ? fallback : Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
