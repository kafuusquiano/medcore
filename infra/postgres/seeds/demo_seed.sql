-- ============================================================
-- MedCore: datos de demostracion.
--
-- Medicos, horarios y pacientes para poder recorrer el circuito completo:
-- cita, atencion, orden de laboratorio, muestra, resultado y aviso. Sin
-- medicos con horario no hay citas que reservar, y sin citas no hay nada que
-- demostrar.
--
-- NO es una migracion de Flyway, a proposito. Flyway lleva el esquema y los
-- datos de referencia que el sistema necesita para funcionar: los roles, el
-- catalogo CIE-10, el catalogo de pruebas. Esto son datos de ejemplo, y meter
-- pacientes inventados en una migracion los llevaria tambien a produccion.
--
-- Se aplica a mano, con el stack levantado:
--
--   docker exec -i medcore-postgres psql -U medcore -d postgres \
--       -f /dev/stdin < infra/postgres/seeds/demo_seed.sql
--
-- O con el script que lo envuelve:
--
--   bash infra/postgres/seeds/aplicar-demo.sh
--
-- Es idempotente: se puede aplicar varias veces sin duplicar nada, porque
-- cada insercion lleva ON CONFLICT DO NOTHING sobre su clave natural.
--
-- ADVERTENCIA: las contrasenas de los usuarios de demostracion son publicas,
-- estan en este archivo y son iguales para todos. Sirven para una
-- demostracion en local y nada mas. Nunca se aplica este seed a una base que
-- no sea de desarrollo.
-- ============================================================


-- ============================================================
-- auth_db: los usuarios del personal.
--
-- La contrasena de todos es "Medcore2026" (hash BCrypt, coste 10). Es la
-- misma para los cuatro porque quien hace la demostracion tiene que poder
-- entrar como cualquiera sin apuntar cuatro claves.
-- ============================================================
\c auth_db

INSERT INTO users (dni, correo, password_hash, nombres, apellidos, telefono,
                   estado, role_id, fecha_creacion, fecha_actualizacion)
SELECT v.dni, v.correo, v.hash, v.nombres, v.apellidos, v.telefono,
       'ACTIVE', r.id, now(), now()
FROM (VALUES
    -- Dos medicos: hacen falta dos para que se vea que la agenda es por
    -- medico y que una cita no bloquea al otro.
    ('40111222', 'ricardo.vega@medcore.local',
     '$2a$10$N.VqF.7UZOtLOHr6BYcbHuwKix2TUsC.h.X17b/l3zrJG7e5c0.lO',
     'Ricardo', 'Vega Campos', '987111222', 'MEDICO'),
    ('40333444', 'carmen.rojas@medcore.local',
     '$2a$10$N.VqF.7UZOtLOHr6BYcbHuwKix2TUsC.h.X17b/l3zrJG7e5c0.lO',
     'Carmen', 'Rojas Ibanez', '987333444', 'MEDICO'),
    -- Un laboratorista: es quien recibe las muestras y valida resultados.
    ('40555666', 'luis.quispe@medcore.local',
     '$2a$10$N.VqF.7UZOtLOHr6BYcbHuwKix2TUsC.h.X17b/l3zrJG7e5c0.lO',
     'Luis', 'Quispe Mamani', '987555666', 'LABORATORISTA'),
    -- Una recepcionista: registra pacientes y reserva citas.
    ('40777888', 'ana.flores@medcore.local',
     '$2a$10$N.VqF.7UZOtLOHr6BYcbHuwKix2TUsC.h.X17b/l3zrJG7e5c0.lO',
     'Ana', 'Flores Huaman', '987777888', 'RECEPCIONISTA')
) AS v (dni, correo, hash, nombres, apellidos, telefono, rol)
JOIN roles r ON r.nombre = v.rol
ON CONFLICT (dni) DO NOTHING;


-- ============================================================
-- patient_db: pacientes repartidos por zonas de Lima.
--
-- Las zonas no son decorativas: la vigilancia epidemiologica agrupa por
-- distrito, y con todos los pacientes en el mismo no se puede ver si el mapa
-- de calor y la deteccion de brotes funcionan.
--
-- Las edades tambien estan repartidas a proposito, para que los rangos de
-- referencia del laboratorio y los grupos de edad de la vigilancia tengan
-- algo que distinguir.
-- ============================================================
\c patient_db

INSERT INTO patients (dni, nombres, apellidos, fecha_nacimiento, sexo,
                      tipo_sangre, telefono, correo, direccion, zona,
                      contacto_emergencia_nombre, contacto_emergencia_telefono,
                      alergias, estado, fecha_creacion, fecha_actualizacion)
VALUES
    ('70100001', 'Elena', 'Soto Vargas', '1989-03-22', 'FEMENINO', 'O_POS',
     '987000101', 'elena.soto@correo.local', 'Av. Los Proceres 123',
     'San Juan de Lurigancho', 'Mario Soto', '987000102',
     'Penicilina', 'ACTIVO', now(), now()),

    ('70100002', 'Jorge', 'Mendoza Ruiz', '1975-11-08', 'MASCULINO', 'A_POS',
     '987000103', 'jorge.mendoza@correo.local', 'Jr. Union 456',
     'San Juan de Lurigancho', 'Luisa Ruiz', '987000104',
     NULL, 'ACTIVO', now(), now()),

    ('70100003', 'Rosa', 'Chavez Lopez', '1994-06-15', 'FEMENINO', 'B_POS',
     '987000105', 'rosa.chavez@correo.local', 'Calle Las Flores 78',
     'Comas', 'Pedro Chavez', '987000106',
     NULL, 'ACTIVO', now(), now()),

    ('70100004', 'Miguel', 'Torres Aguilar', '1962-01-30', 'MASCULINO', 'O_NEG',
     '987000107', 'miguel.torres@correo.local', 'Av. Tupac Amaru 1020',
     'Comas', 'Sara Aguilar', '987000108',
     'Sulfas, AINE', 'ACTIVO', now(), now()),

    -- Un lactante: el unico con el que se puede comprobar que los rangos
    -- pediatricos y el grupo "MENOR DE 1" se aplican de verdad.
    ('70100005', 'Mateo', 'Ramirez Diaz', CURRENT_DATE - INTERVAL '7 months',
     'MASCULINO', 'A_POS', '987000109', 'carla.diaz@correo.local',
     'Pasaje Los Olivos 12', 'Villa El Salvador', 'Carla Diaz', '987000110',
     NULL, 'ACTIVO', now(), now()),

    ('70100006', 'Lucia', 'Paredes Nunez', '2012-09-03', 'FEMENINO', 'AB_POS',
     '987000111', 'lucia.paredes@correo.local', 'Av. Revolucion 340',
     'Villa El Salvador', 'Teresa Nunez', '987000112',
     NULL, 'ACTIVO', now(), now()),

    ('70100007', 'Carlos', 'Huaman Quispe', '1948-04-19', 'MASCULINO', 'O_POS',
     '987000113', 'carlos.huaman@correo.local', 'Calle Grau 55',
     'Ate', 'Julia Quispe', '987000114',
     NULL, 'ACTIVO', now(), now()),

    ('70100008', 'Patricia', 'Cordova Silva', '1983-12-11', 'FEMENINO', 'B_NEG',
     '987000115', 'patricia.cordova@correo.local', 'Av. Nicolas Ayllon 880',
     'Ate', 'Raul Silva', '987000116',
     'Yodo', 'ACTIVO', now(), now()),

    ('70100009', 'Andres', 'Salazar Pena', '2001-07-25', 'MASCULINO', 'A_NEG',
     '987000117', 'andres.salazar@correo.local', 'Jr. Ancash 210',
     'Cercado de Lima', 'Nora Pena', '987000118',
     NULL, 'ACTIVO', now(), now()),

    ('70100010', 'Gloria', 'Vilca Condori', '1970-02-14', 'FEMENINO', 'O_POS',
     '987000119', 'gloria.vilca@correo.local', 'Av. Abancay 1500',
     'Cercado de Lima', 'Hugo Condori', '987000120',
     NULL, 'ACTIVO', now(), now())
ON CONFLICT (dni) DO NOTHING;


-- ============================================================
-- appointment_db: horarios de los dos medicos.
--
-- Los dias van como nombres de java.time.DayOfWeek separados por comas, que
-- es lo que lee la entidad.
--
-- Los horarios no se solapan del todo a proposito: con dos agendas distintas
-- se puede comprobar que los huecos se calculan por medico y que ocupar uno
-- no afecta al otro.
-- ============================================================
\c appointment_db

INSERT INTO doctor_schedule (id_medico, nombre_medico, especialidad,
                             dias_disponibles, hora_inicio, hora_fin, activo)
SELECT v.id_medico, v.nombre, v.especialidad, v.dias, v.inicio::time,
       v.fin::time, true
FROM (VALUES
    (1, 'Dr. Ricardo Vega Campos', 'Medicina interna',
     'MONDAY,TUESDAY,WEDNESDAY,THURSDAY,FRIDAY', '08:00', '14:00'),
    (2, 'Dra. Carmen Rojas Ibanez', 'Medicina general',
     'MONDAY,WEDNESDAY,FRIDAY', '14:00', '20:00')
) AS v (id_medico, nombre, especialidad, dias, inicio, fin)
WHERE NOT EXISTS (
    SELECT 1 FROM doctor_schedule d WHERE d.id_medico = v.id_medico
);


-- ============================================================
-- Que queda listo
-- ============================================================
\c postgres

\echo ''
\echo 'Datos de demostracion aplicados.'
\echo ''
\echo '  4 usuarios del personal (2 medicos, 1 laboratorista, 1 recepcionista)'
\echo '  10 pacientes en 5 zonas de Lima, de 7 meses a 78 anios'
\echo '  2 horarios medicos'
\echo ''
\echo 'Entrar con el DNI y la contrasena Medcore2026:'
\echo '  40111222  Dr. Ricardo Vega      MEDICO'
\echo '  40333444  Dra. Carmen Rojas     MEDICO'
\echo '  40555666  Luis Quispe           LABORATORISTA'
\echo '  40777888  Ana Flores            RECEPCIONISTA'
\echo ''
\echo 'El administrador lo crea AdminBootstrap al arrancar auth-service, con'
\echo 'el DNI 00000001 y la contrasena de ADMIN_INITIAL_PASSWORD del .env.'
\echo ''
