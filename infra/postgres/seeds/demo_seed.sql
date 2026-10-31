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
-- inventory_db: lotes en el almacen.
--
-- La migracion V1 siembra el CATALOGO de reactivos, que es un dato de
-- referencia: que reactivos usa el laboratorio. Pero no siembra lotes, porque
-- un lote es material que alguien recibio un dia concreto con un vencimiento
-- concreto, y eso no es referencia.
--
-- Sin lotes el almacen esta vacio, y el descuento automatico no tiene de
-- donde descontar: cada resultado deja un WARN de stock insuficiente en el
-- log. Es el comportamiento correcto, pero no se puede demostrar nada.
--
-- Dos lotes de los reactivos de hematologia a proposito, uno proximo a vencer
-- y otro no: asi se ve que el consumo gasta primero el que vence antes, que es
-- la regla del almacen y lo que evita tirar material.
--
-- Uno de los reactivos queda por debajo de su minimo a proposito, para que la
-- alerta de reposicion tenga algo que avisar.
-- ============================================================
\c inventory_db

INSERT INTO stock (reagent_id, lote, cantidad_actual, cantidad_inicial,
                   fecha_vencimiento, fecha_ingreso, ubicacion, observaciones)
SELECT r.id, v.lote, v.cantidad, v.cantidad,
       CURRENT_DATE + (v.dias_para_vencer || ' days')::interval,
       CURRENT_DATE - INTERVAL '10 days',
       v.ubicacion, v.nota
FROM (VALUES
    -- Hematologia: dos lotes del mismo reactivo, con vencimientos distintos.
    ('REA-HEM-01', 'L-HEM01-A', 40,  45, 'Refrigerador 1', 'Vence pronto: se gasta primero'),
    ('REA-HEM-01', 'L-HEM01-B', 120, 365, 'Refrigerador 1', NULL),
    ('REA-HEM-02', 'L-HEM02-A', 200, 200, 'Almacen general', NULL),
    ('REA-HEM-03', 'L-HEM03-A', 150, 180, 'Almacen general', NULL),

    -- Bioquimica.
    ('REA-BIO-01', 'L-BIO01-A', 180, 240, 'Refrigerador 2', NULL),
    ('REA-BIO-02', 'L-BIO02-A', 90,  200, 'Refrigerador 2', NULL),
    ('REA-BIO-03', 'L-BIO03-A', 110, 300, 'Refrigerador 2', NULL),
    -- Este queda por debajo del minimo (20): la alerta tiene que saltar.
    ('REA-BIO-04', 'L-BIO04-A', 12,  60,  'Refrigerador 2', 'Pendiente de reposicion'),

    -- Inmunologia y microbiologia.
    ('REA-INM-01', 'L-INM01-A', 60,  120, 'Refrigerador 1', NULL),
    ('REA-MIC-01', 'L-MIC01-A', 48,  30,  'Camara de cultivo', NULL),
    ('REA-MIC-02', 'L-MIC02-A', 48,  30,  'Camara de cultivo', NULL),
    ('REA-MIC-03', 'L-MIC03-A', 75,  90,  'Almacen general', NULL)
) AS v (codigo_reactivo, lote, cantidad, dias_para_vencer, ubicacion, nota)
JOIN reagents r ON r.codigo = v.codigo_reactivo
WHERE NOT EXISTS (
    SELECT 1 FROM stock s WHERE s.reagent_id = r.id AND s.lote = v.lote
);


-- ============================================================
-- appointment_db: los horarios NO se siembran aqui.
--
-- Crear un horario no es insertar una fila: el servicio genera a la vez los
-- huecos de las proximas semanas en time_slots, y sin esos huecos no se puede
-- reservar ninguna cita. Un INSERT directo en doctor_schedule deja el horario
-- puesto y la agenda vacia, que es peor que no tener horario: el sistema
-- parece configurado y no lo esta.
--
-- Los crea scripts/preparar_demo.py llamando a la API, que es la unica forma
-- de que la generacion de huecos ocurra. Lo lanza aplicar-demo.sh despues de
-- este archivo.
-- ============================================================


-- ============================================================
-- Que queda listo
-- ============================================================
\c postgres

\echo ''
\echo 'Datos de demostracion aplicados.'
\echo ''
\echo '  4 usuarios del personal (2 medicos, 1 laboratorista, 1 recepcionista)'
\echo '  10 pacientes en 5 zonas de Lima, de 7 meses a 78 anios'
\echo '  12 lotes de reactivo en el almacen'
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
