-- ============================================================
-- auth_db V1: roles y usuarios.
-- ============================================================

CREATE TABLE roles (
    id          BIGSERIAL PRIMARY KEY,
    nombre      VARCHAR(30)  NOT NULL UNIQUE,
    descripcion VARCHAR(150)
);

CREATE TABLE users (
    id                  BIGSERIAL PRIMARY KEY,
    dni                 VARCHAR(8)   NOT NULL UNIQUE,
    correo              VARCHAR(120) NOT NULL UNIQUE,
    password_hash       VARCHAR(100) NOT NULL,
    nombres             VARCHAR(80)  NOT NULL,
    apellidos           VARCHAR(80)  NOT NULL,
    telefono            VARCHAR(15),
    estado              VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    role_id             BIGINT       NOT NULL REFERENCES roles (id),
    intentos_fallidos   INTEGER      NOT NULL DEFAULT 0,
    id_paciente         BIGINT,
    ultimo_acceso       TIMESTAMPTZ,
    fecha_creacion      TIMESTAMPTZ  NOT NULL,
    fecha_actualizacion TIMESTAMPTZ  NOT NULL,
    version             BIGINT       NOT NULL DEFAULT 0,

    CONSTRAINT chk_users_estado CHECK (estado IN ('ACTIVE', 'INACTIVE', 'BLOCKED')),
    CONSTRAINT chk_users_dni    CHECK (dni ~ '^[0-9]{8}$')
);

-- Un paciente no puede tener dos cuentas. El indice unico PARCIAL permite que
-- los usuarios internos, que no tienen paciente asociado, repitan NULL.
CREATE UNIQUE INDEX ux_users_id_paciente ON users (id_paciente) WHERE id_paciente IS NOT NULL;
CREATE INDEX ix_users_role      ON users (role_id);
CREATE INDEX ix_users_estado    ON users (estado);
-- Busqueda de login por correo en minusculas, sin distinguir mayusculas.
CREATE INDEX ix_users_correo_lower ON users (LOWER(correo));

INSERT INTO roles (nombre, descripcion) VALUES
    ('ADMIN',         'Administra usuarios, catalogos y parametros del sistema'),
    ('RECEPCIONISTA', 'Registra pacientes y gestiona la agenda de citas'),
    ('MEDICO',        'Atiende consultas, emite diagnosticos y ordenes de laboratorio'),
    ('LABORATORISTA', 'Registra muestras y resultados de laboratorio'),
    ('PACIENTE',      'Consulta sus propias citas y resultados');
