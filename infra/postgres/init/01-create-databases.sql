-- ============================================================
-- MedCore — creacion de las bases de datos de cada microservicio.
-- Se ejecuta una sola vez, al inicializar el volumen de Postgres.
-- Regla MC1: cada servicio tiene su propia base y no accede a la de otro.
-- ============================================================

CREATE DATABASE auth_db;
CREATE DATABASE patient_db;
CREATE DATABASE appointment_db;
CREATE DATABASE medical_db;
CREATE DATABASE lab_db;
CREATE DATABASE inventory_db;
CREATE DATABASE notification_db;

-- El usuario definido en POSTGRES_USER es propietario de todas.
-- Las tablas las crea Flyway desde cada servicio (db/migration).
