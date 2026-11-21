"""End-to-end checks for the Haven Hospital API.

Run against a running backend:

    python tests/api_test.py

Optional environment variables:
    API_BASE       default http://127.0.0.1:8080/api
    ADMIN_EMAIL    default admin@haven.local
    ADMIN_PASSWORD default Admin12345 (the backend/.env value you set)

The script creates uniquely named doctors and a patient, so it can be run more
than once against the same database. It prints one line per failed check and
exits with status 1 if anything fails.
"""

import json
import os
import sys
import urllib.error
import urllib.request
import uuid

BASE = os.getenv("API_BASE", "http://127.0.0.1:8080/api")
ADMIN_EMAIL = os.getenv("ADMIN_EMAIL", "admin@haven.local")
ADMIN_PASSWORD = os.getenv("ADMIN_PASSWORD", "Admin12345")
SUFFIX = uuid.uuid4().hex[:6]

results = []


def call(method, path, body=None, token=None, expect=200, headers=None):
    accepted = expect if isinstance(expect, (tuple, list, set)) else (expect,)
    request = urllib.request.Request(
        BASE + path,
        data=None if body is None else json.dumps(body).encode(),
        method=method,
    )
    if body is not None:
        request.add_header("Content-Type", "application/json")
    if token:
        request.add_header("Authorization", "Bearer " + token)
    for name, value in (headers or {}).items():
        request.add_header(name, value)
    try:
        with urllib.request.urlopen(request, timeout=30) as response:
            status, raw = response.status, response.read().decode() or "null"
    except urllib.error.HTTPError as error:
        status, raw = error.code, error.read().decode() or "null"
    except urllib.error.URLError as error:
        print(f"Cannot reach {BASE}: {error.reason}")
        print("Start the Java backend first (backend/run.bat or backend/run.sh).")
        raise SystemExit(2)
    try:
        payload = json.loads(raw)
    except json.JSONDecodeError:
        payload = raw
    record(status in accepted, f"{method} {path} -> {status} (expected {expect})")
    if status not in accepted:
        print(f"    response: {payload}")
    return payload


def record(condition, label):
    results.append((bool(condition), label))
    if not condition:
        print(f"    failed: {label}")


def check(condition, label):
    record(condition, label)


print("health and authentication")
health = call("GET", "/health")
check(health.get("status") == "ok", "health endpoint reports ok")
check(str(health.get("database", "")).startswith("ok"), "database is reachable")
check(health.get("database") in ("ok", "unavailable"), "health reports a safe database status")
check("MySQL" not in str(health.get("database", "")), "health does not expose the database version")

call("POST", "/auth/login", {"email": ADMIN_EMAIL, "password": "definitely-wrong-1"}, expect=401)
call("GET", "/admin/stats", expect=401)
admin = call("POST", "/auth/login", {"email": ADMIN_EMAIL, "password": ADMIN_PASSWORD, "role": "admin"})
admin_token = admin["token"]
check(admin["user"]["role"] == "ADMIN", "administrator role is returned")
me = call("GET", "/auth/me", token=admin_token)
check(me["email"] == ADMIN_EMAIL, "session resolves to the authenticated account")

print("doctor management")
call(
    "POST",
    "/admin/doctors",
    {"firstName": "Weak", "lastName": "Password", "specialty": "Cardiology",
     "email": f"weak-{SUFFIX}@test.local", "password": "short"},
    token=admin_token,
    expect=400,
)
call("POST", "/auth/login", {"email": ADMIN_EMAIL, "password": 12345678}, expect=400)
doctor = call(
    "POST",
    "/admin/doctors",
    {"firstName": "Test", "lastName": f"Doctor{SUFFIX}", "specialty": "Cardiology",
     "email": f"doctor-{SUFFIX}@test.local", "password": "Doctor12345",
     "phone": "22000000", "office": "Room 1"},
    token=admin_token,
    expect=201,
)
doctor_id = doctor["id"]
check(bool(doctor["staffId"]), "staff ID assigned automatically")
call(
    "POST",
    "/admin/doctors",
    {"firstName": "Other", "lastName": "Doctor", "specialty": "Neurology",
     "email": f"doctor-{SUFFIX}@test.local", "password": "Doctor12345"},
    token=admin_token,
    expect=409,
)
found = call("GET", f"/admin/doctors?q=Doctor{SUFFIX}", token=admin_token)
check(any(d["id"] == doctor_id for d in found), "doctor search finds the new doctor")

print("password whitespace is preserved exactly")
space_email = f"space-{SUFFIX}@test.local"
space_password = "  Space12345  "
space_doctor = call(
    "POST",
    "/admin/doctors",
    {"firstName": "Space", "lastName": f"Doctor{SUFFIX}", "specialty": "Cardiology",
     "email": space_email, "password": space_password},
    token=admin_token,
    expect=201,
)
call("POST", "/auth/login", {"email": space_email, "password": "Space12345"}, expect=401)
space_session = call("POST", "/auth/login",
                     {"email": space_email, "password": space_password, "role": "doctor"})
check("token" in space_session, "a password with spaces must be typed exactly")
call("PATCH", f"/admin/doctors/{doctor_id}/status", {"status": "leave"}, token=admin_token)
on_leave = call("GET", "/admin/doctors?status=leave", token=admin_token)
check(any(d["id"] == doctor_id for d in on_leave), "status filter returns the doctor on leave")
call("PATCH", f"/admin/doctors/{doctor_id}/status", {"status": "active"}, token=admin_token)

print("doctor session and scoped data")
call("POST", "/auth/login",
     {"email": f"doctor-{SUFFIX}@test.local", "password": "Doctor12345", "role": "admin"}, expect=403)
doctor_session = call("POST", "/auth/login",
                      {"email": f"doctor-{SUFFIX}@test.local", "password": "Doctor12345", "role": "doctor"})
doctor_token = doctor_session["token"]
check(doctor_session["user"]["doctorId"] == doctor_id, "doctor identity comes from the session")
call("GET", "/admin/stats", token=doctor_token, expect=403)
call("GET", "/admin/users", token=doctor_token, expect=403)

print("forwarded headers cannot bypass throttling")
spoofed_email = f"nobody-{SUFFIX}@test.local"
for attempt in range(8):
    call("POST", "/auth/login", {"email": spoofed_email, "password": "Wrong12345"},
         headers={"X-Forwarded-For": f"203.0.113.{attempt + 1}"}, expect=401)
call("POST", "/auth/login", {"email": spoofed_email, "password": "Wrong12345"},
     headers={"X-Forwarded-For": "198.51.100.99"}, expect=429)

print("patients")
patient = call(
    "POST",
    "/admin/patients",
    {"firstName": "Test", "lastName": f"Patient{SUFFIX}", "birthDate": "1992-03-04",
     "gender": "Female", "phone": "22111111", "address": "Sousse"},
    token=admin_token,
    expect=201,
)
patient_id = patient["id"]

print("atomic patient creation")
missing_doctor_last = f"MissingDoctor{SUFFIX}"
call(
    "POST",
    "/admin/patients",
    {"firstName": "Orphan", "lastName": missing_doctor_last, "birthDate": "1990-01-01",
     "doctorId": 999999},
    token=admin_token,
    expect=404,
)
orphans = call("GET", f"/admin/patients?q={missing_doctor_last}", token=admin_token)
check(orphans == [], "a failed doctor assignment must not leave an orphan patient")
call(
    "POST",
    "/admin/patients",
    {"firstName": "Future", "lastName": f"Patient{SUFFIX}", "birthDate": "2999-01-01"},
    token=admin_token,
    expect=400,
)
call(
    "PUT",
    f"/admin/patients/{patient_id}",
    {"firstName": "Test", "lastName": f"Patient{SUFFIX}", "birthDate": "2999-01-01"},
    token=admin_token,
    expect=400,
)
updated_patient = call(
    "PUT",
    f"/admin/patients/{patient_id}",
    {"firstName": "Test", "lastName": f"Patient{SUFFIX}", "birthDate": "1992-03-04",
     "gender": "Female", "phone": "22111112", "address": "Sousse"},
    token=admin_token,
)
check(updated_patient["phone"] == "22111112", "patient update is persisted")

visible = call("GET", "/doctors/me/patients", token=doctor_token)
check(all(p["id"] != patient_id for p in visible), "a patient is invisible before being assigned")
call("PUT", f"/admin/patients/{patient_id}/doctor", {"doctorId": doctor_id}, token=admin_token)
visible = call("GET", "/doctors/me/patients", token=doctor_token)
check(any(p["id"] == patient_id for p in visible), "assigned patient is visible to the doctor")

print("appointments and consultations")
appointment = call(
    "POST",
    "/doctors/me/appointments",
    {"patientId": patient_id, "appointmentDate": "2027-01-10T09:30:00", "reason": "Checkup"},
    token=doctor_token,
    expect=201,
)
appointment_id = appointment["id"]
check(appointment["patient"]["id"] == patient_id, "appointment contains its patient")
call("POST", "/doctors/me/appointments",
     {"patientId": 999999, "appointmentDate": "2027-01-11T09:00:00", "reason": "Ghost"},
     token=doctor_token, expect=400)
call("POST", f"/doctors/me/consultations",
     {"appointmentId": appointment_id, "diagnosis": "Too early"}, token=doctor_token, expect=400)
call("PATCH", f"/doctors/me/appointments/{appointment_id}/status", {"status": "completed"}, token=doctor_token)
consultation = call(
    "POST",
    "/doctors/me/consultations",
    {"appointmentId": appointment_id, "diagnosis": "Migraine", "treatment": "Rest", "notes": "Follow up"},
    token=doctor_token,
    expect=201,
)
call("POST", "/doctors/me/consultations",
     {"appointmentId": appointment_id, "diagnosis": "Duplicate"}, token=doctor_token, expect=409)
history = call("GET", f"/doctors/me/patients/{patient_id}/consultations", token=doctor_token)
check(len(history) == 1 and history[0]["diagnosis"] == "Migraine", "consultation history is stored")
call("PUT", f"/doctors/me/consultations/{consultation['id']}",
     {"diagnosis": "Migraine with aura", "treatment": "Rest", "notes": "Follow up"},
     token=doctor_token)
history = call("GET", f"/doctors/me/patients/{patient_id}/consultations", token=doctor_token)
check(history[0]["diagnosis"] == "Migraine with aura", "consultation update is persisted")

print("rescheduling")
appointment = call(
    "PUT",
    f"/doctors/me/appointments/{appointment_id}",
    {"patientId": patient_id, "appointmentDate": "2027-01-12T14:00:00", "reason": "Moved"},
    token=doctor_token,
)
check(appointment["appointmentDate"].startswith("2027-01-12T14:00"), "appointment moved")
cancelled = call("PATCH", f"/doctors/me/appointments/{appointment_id}/status",
                 {"status": "cancelled"}, token=doctor_token)
check(cancelled["status"] == "cancelled", "appointment cancellation is persisted")
call("PUT", f"/doctors/me/appointments/{appointment_id}",
     {"patientId": patient_id, "appointmentDate": "1800-01-01T09:00:00"},
     token=doctor_token, expect=400)

print("doctor profile")
profile = call(
    "PATCH",
    "/doctors/me",
    {"name": "Dr. Test Doctor", "phone": "22999888", "office": "Room 9",
     "workDays": [1, 2, 3, 4], "firstAppointment": "09:00", "lastAppointment": "16:00", "visitLength": 20},
    token=doctor_token,
)
check(profile["workDays"] == [1, 2, 3, 4], "working days saved")
check(profile["visitLength"] == 20, "visit length saved")

print("cross-doctor isolation")
other = call(
    "POST",
    "/admin/doctors",
    {"firstName": "Second", "lastName": f"Doctor{SUFFIX}", "specialty": "Dermatology",
     "email": f"second-{SUFFIX}@test.local", "password": "Doctor12345"},
    token=admin_token,
    expect=201,
)
other_session = call("POST", "/auth/login",
                     {"email": f"second-{SUFFIX}@test.local", "password": "Doctor12345", "role": "doctor"})
other_token = other_session["token"]
call("GET", f"/doctors/me/patients/{patient_id}", token=other_token, expect=404)
call("GET", f"/doctors/me/patients/{patient_id}/consultations", token=other_token, expect=404)
call("PATCH", f"/doctors/me/appointments/{appointment_id}/status", {"status": "cancelled"},
     token=other_token, expect=404)
call("POST", "/doctors/me/consultations",
     {"appointmentId": appointment_id, "diagnosis": "Not my appointment"},
     token=other_token, expect=404)

print("invalid query strings")
call("GET", "/admin/patients?q=%zz", token=admin_token, expect=400)

print("hospital settings")
call("PUT", "/admin/settings", {"name": "Haven Hospital", "visiting": {"start": "20:00", "end": "08:00"}},
     token=admin_token, expect=400)
saved = call(
    "PUT",
    "/admin/settings",
    {"name": "Haven Hospital", "phone": "22000000", "erNumber": "190", "email": "hello@haven.local",
     "street": "12 Avenue Habib Bourguiba", "cityState": "Sousse, Tunisia",
     "parking": "Free patient parking", "visiting": {"start": "10:00", "end": "19:00", "maxVisitors": 3}},
    token=admin_token,
)
check(saved["visiting"]["maxVisitors"] == 3, "settings saved")
public = call("GET", "/public/hospital")
check(public["phone"] == "22000000", "public settings reflect the change")
call("PUT", "/admin/settings", {"name": "Haven Hospital"}, token=doctor_token, expect=403)

print("AI gateway")
assistant = call("POST", "/assistant/chat", {"message": "When can I visit?"}, expect=(200, 503))
if isinstance(assistant, dict) and "response" in assistant:
    check(bool(assistant["response"]), "AI assistant answered")
    print("    (AI service is running; answer received)")
else:
    print("    (AI service is not configured; the gateway correctly refused)")

print("password change, session revocation and logout")
second_session = call("POST", "/auth/login",
                      {"email": f"second-{SUFFIX}@test.local", "password": "Doctor12345", "role": "doctor"})
call("POST", "/auth/password", {"currentPassword": "wrong12345", "newPassword": "Doctor123456"},
     token=other_token, expect=400)
call("POST", "/auth/password", {"currentPassword": "Doctor12345", "newPassword": "Doctor123456"},
     token=other_token)
call("POST", "/auth/login",
     {"email": f"second-{SUFFIX}@test.local", "password": "Doctor12345", "role": "doctor"}, expect=401)
call("GET", "/auth/me", token=other_token)
call("GET", "/auth/me", token=second_session["token"], expect=401)
relogin = call("POST", "/auth/login",
               {"email": f"second-{SUFFIX}@test.local", "password": "Doctor123456", "role": "doctor"})
call("POST", "/auth/logout", token=relogin["token"], expect=204)
call("GET", "/auth/me", token=relogin["token"], expect=401)

print("reset password keeps exact whitespace and revokes sessions")
reset_password = "  Reset12345  "
call("POST", f"/admin/users/{space_doctor['userId']}/password", {"password": reset_password},
     token=admin_token)
call("POST", "/auth/login", {"email": space_email, "password": reset_password, "role": "doctor"})
call("POST", "/auth/login", {"email": space_email, "password": "Reset12345", "role": "doctor"},
     expect=401)
call("GET", "/doctors/me", token=space_session["token"], expect=401)

print("deactivation")
call("DELETE", f"/admin/doctors/{doctor_id}", token=admin_token)
call("POST", "/auth/login",
     {"email": f"doctor-{SUFFIX}@test.local", "password": "Doctor12345", "role": "doctor"}, expect=403)
call("GET", "/doctors/me", token=doctor_token, expect=401)
reactivated = call("PATCH", f"/admin/doctors/{doctor_id}/status", {"status": "active"}, token=admin_token)
check(reactivated["status"] == "active", "doctor reactivated")
call("GET", "/doctors/me", token=doctor_token, expect=401)
session_again = call("POST", "/auth/login",
                     {"email": f"doctor-{SUFFIX}@test.local", "password": "Doctor12345", "role": "doctor"})
check("token" in session_again, "reactivated doctor can sign in")

print("profile edit deactivation revokes sessions too")
call("PUT", f"/admin/doctors/{doctor_id}", {"status": "inactive"}, token=admin_token)
call("GET", "/doctors/me", token=session_again["token"], expect=401)
call("PATCH", f"/admin/doctors/{doctor_id}/status", {"status": "active"}, token=admin_token)
call("GET", "/doctors/me", token=session_again["token"], expect=401)
final_login = call("POST", "/auth/login",
                   {"email": f"doctor-{SUFFIX}@test.local", "password": "Doctor12345", "role": "doctor"})
call("GET", "/doctors/me", token=final_login["token"])

print("account administration")
call("PATCH", f"/admin/users/{admin['user']['id']}/active", {"active": False}, token=admin_token, expect=400)
accounts = call("GET", "/admin/users", token=admin_token)
check(any(a["email"] == f"doctor-{SUFFIX}@test.local" for a in accounts), "accounts listing includes the doctor")
call("POST", f"/admin/users/{other_session['user']['id']}/password", {"password": "weak"},
     token=admin_token, expect=400)

print("statistics")
stats = call("GET", "/admin/stats", token=admin_token)
check(stats["doctors"] >= 2 and stats["patients"] >= 1, "stats count doctors and patients")
check(stats["consultations"] >= 1, "stats count consultations")
activity = call("GET", "/admin/activity", token=admin_token)
check(len(activity) > 0, "activity feed is populated")

failed = [label for ok, label in results if not ok]
print()
print(f"{len(results) - len(failed)}/{len(results)} checks passed")
for label in failed:
    print("FAILED:", label)
sys.exit(1 if failed else 0)
