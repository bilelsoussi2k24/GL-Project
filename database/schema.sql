-- Reference schema for Haven Hospital (MySQL 8).
--
-- You normally do NOT need this file: the Java backend creates and upgrades the
-- schema itself on start (config/DatabaseMigrator, additive and idempotent).
-- It is provided so the data model is readable and so a DBA can review it.
--
-- This file is non-destructive: it only creates missing tables. It does not
-- drop data. Password hashes must be produced by the application (PBKDF2);
-- accounts are created through the API or the tool.DbSetup command.

CREATE DATABASE IF NOT EXISTS hospital_db
  CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
USE hospital_db;

CREATE TABLE IF NOT EXISTS users (
  id int NOT NULL AUTO_INCREMENT,
  email varchar(150) NOT NULL,
  password_hash varchar(255) NOT NULL,
  role enum('ADMIN','DOCTOR') NOT NULL,
  active tinyint(1) NOT NULL DEFAULT 1,
  full_name varchar(120) NOT NULL DEFAULT '',
  phone varchar(20) DEFAULT NULL,
  password_changed_at datetime DEFAULT NULL,
  created_at datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY email (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS doctors (
  id int NOT NULL AUTO_INCREMENT,
  user_id int NOT NULL,
  staff_id varchar(20) DEFAULT NULL,
  first_name varchar(50) NOT NULL,
  last_name varchar(50) NOT NULL,
  specialty varchar(100) NOT NULL,
  phone varchar(20) DEFAULT NULL,
  office varchar(80) DEFAULT NULL,
  status enum('active','leave','inactive') NOT NULL DEFAULT 'active',
  work_days varchar(20) NOT NULL DEFAULT '1,2,3,4,5',
  first_appointment time NOT NULL DEFAULT '08:00:00',
  last_appointment time NOT NULL DEFAULT '17:00:00',
  visit_length int NOT NULL DEFAULT 30,
  created_at datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY user_id (user_id),
  UNIQUE KEY staff_id (staff_id),
  CONSTRAINT doctors_ibfk_1 FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS patients (
  id int NOT NULL AUTO_INCREMENT,
  first_name varchar(50) NOT NULL,
  last_name varchar(50) NOT NULL,
  birth_date date NOT NULL,
  gender varchar(20) DEFAULT NULL,
  phone varchar(20) DEFAULT NULL,
  address varchar(255) DEFAULT NULL,
  created_at datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_patients_name (last_name, first_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS doctor_patients (
  doctor_id int NOT NULL,
  patient_id int NOT NULL,
  assigned_at datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (doctor_id, patient_id),
  KEY patient_id (patient_id),
  CONSTRAINT doctor_patients_ibfk_1 FOREIGN KEY (doctor_id) REFERENCES doctors (id),
  CONSTRAINT doctor_patients_ibfk_2 FOREIGN KEY (patient_id) REFERENCES patients (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS appointments (
  id int NOT NULL AUTO_INCREMENT,
  doctor_id int NOT NULL,
  patient_id int NOT NULL,
  appointment_date datetime NOT NULL,
  status enum('scheduled','completed','cancelled','missed') NOT NULL DEFAULT 'scheduled',
  reason varchar(255) DEFAULT NULL,
  created_at datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY doctor_id (doctor_id, patient_id),
  KEY idx_appointments_date (appointment_date),
  CONSTRAINT appointments_ibfk_1 FOREIGN KEY (doctor_id, patient_id)
    REFERENCES doctor_patients (doctor_id, patient_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS consultations (
  id int NOT NULL AUTO_INCREMENT,
  appointment_id int NOT NULL,
  diagnosis text,
  treatment text,
  notes text,
  consultation_date datetime DEFAULT CURRENT_TIMESTAMP,
  updated_at datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY appointment_id (appointment_id),
  CONSTRAINT consultations_ibfk_1 FOREIGN KEY (appointment_id) REFERENCES appointments (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS auth_sessions (
  id int NOT NULL AUTO_INCREMENT,
  token_hash char(64) NOT NULL,
  user_id int NOT NULL,
  created_at datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  expires_at datetime NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY token_hash (token_hash),
  KEY idx_sessions_user (user_id),
  KEY idx_sessions_expiry (expires_at),
  CONSTRAINT sessions_ibfk_1 FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS hospital_settings (
  setting_key varchar(60) NOT NULL,
  setting_value text,
  PRIMARY KEY (setting_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT IGNORE INTO hospital_settings (setting_key, setting_value) VALUES
  ('name', 'Haven Hospital'),
  ('phone', '[PHONE NUMBER]'),
  ('erNumber', '[ER NUMBER]'),
  ('email', '[EMAIL]'),
  ('street', '[STREET ADDRESS]'),
  ('cityState', '[CITY], [STATE]'),
  ('parking', '[PARKING DETAILS]'),
  ('visitingStart', '10:00'),
  ('visitingEnd', '19:00'),
  ('maxVisitors', '2');
