// ============================================================
// MedCore — colecciones e indices de MongoDB.
//
// Se ejecuta una sola vez, al inicializar el volumen de Mongo. Es a MongoDB
// lo que las migraciones de Flyway son a Postgres: la estructura la declara
// la infraestructura, no la aplicacion. Por eso los servicios arrancan con
// spring.data.mongodb.auto-index-creation en false; crear indices desde la
// aplicacion chocaria con los de aqui por el nombre.
//
// Al cambiar este archivo hay que aplicarlo tambien al volumen que ya
// existe, porque Mongo no vuelve a ejecutarlo:
//   docker exec -i medcore-mongodb mongosh -u medcore -p medcore \
//     --authenticationDatabase admin < infra/mongo/init/01-init-collections.js
// Las instrucciones son idempotentes: createCollection sobre una coleccion
// existente falla y se ignora, y createIndex sobre un indice identico no
// hace nada.
//
//   lab_results_db  → laboratory-service (resultado crudo del equipo)
//   analytics_db    → analytics-service (epidemiologia, brotes)
// ============================================================

// ---------- lab_results_db ----------
//
// Guarda el resultado tal como lo emitio el equipo, con su trama original.
// Vive aqui y no en Postgres porque su forma varia con cada fabricante: un
// analizador de hematologia envia parametros que uno de bioquimica no tiene.
//
// El resultado ya interpretado, con su valor y su rango, esta en lab_db
// (Postgres). Esta coleccion no se consulta por prueba ni por valor: solo
// por la muestra a la que pertenece, para auditar o reprocesar. De ahi que
// no haya indices por codigoPrueba ni por interpretacion.
var lab = db.getSiblingDB('lab_results_db');

try {
    lab.createCollection('lab_results');
} catch (e) {
    print('lab_results ya existia: ' + e.codeName);
}

// Unico por muestra y mensaje. Un reenvio del equipo, que ocurre cuando no
// recibe el ACK a tiempo, repite el control id y queda fuera; una tanda nueva
// de la misma muestra, como una formula diferencial despues del hemograma,
// trae otro control id y si entra.
//
// No basta con idMuestra sola: con ese indice el reenvio fallaba con
// DuplicateKeyException y el equipo recibia un ACK de error por un mensaje
// que ya estaba guardado.
lab.lab_results.createIndex(
    { idMuestra: 1, idMensaje: 1 },
    { unique: true, name: 'muestra_mensaje' });
// Todos los mensajes de una muestra, del mas reciente al mas antiguo.
lab.lab_results.createIndex({ idMuestra: 1, fechaRecepcion: -1 }, { name: 'muestra_fecha' });
// Busqueda con el codigo de la etiqueta del tubo en la mano.
lab.lab_results.createIndex({ codigoMuestra: 1 }, { name: 'codigoMuestra' });
// Trazabilidad: que envio cada equipo y cuando.
lab.lab_results.createIndex({ equipo: 1, fechaRecepcion: -1 }, { name: 'equipo_fecha' });
lab.lab_results.createIndex({ idPaciente: 1, fechaRecepcion: -1 }, { name: 'paciente_fecha' });

// ---------- analytics_db ----------
var analytics = db.getSiblingDB('analytics_db');

try {
    analytics.createCollection('epidemiological_records');
} catch (e) {
    print('epidemiological_records ya existia: ' + e.codeName);
}

// Unico: hace idempotente el consumo de result.registered. Kafka puede
// entregar el mismo evento dos veces, y un caso contado doble falsea la
// vigilancia entera.
analytics.epidemiological_records.createIndex(
    { idResultado: 1 },
    { unique: true, name: 'idResultado' });

// Los indices siguen a los campos que analytics recibe de verdad. Esta
// coleccion vigila por PRUEBA de laboratorio anormal; la vigilancia por codigo
// CIE-10 vive en diagnosis_records, mas abajo, porque son dos fuentes con
// campos distintos: un diagnostico no tiene valor numerico ni unidad ni
// prueba, y un resultado no tiene codigo CIE-10.
analytics.epidemiological_records.createIndex({ zona: 1, fecha: -1 });
analytics.epidemiological_records.createIndex({ codigoPrueba: 1, fecha: -1 });
// El indice de la deteccion de brotes: casos de una prueba, en una zona,
// dentro de una ventana de dias. anormal va primero porque descarta de
// entrada la mayoria de los registros.
analytics.epidemiological_records.createIndex(
    { anormal: 1, zona: 1, codigoPrueba: 1, fecha: -1 },
    { name: 'deteccion_brotes' });

// ---------- diagnosis_records ----------
//
// La otra mitad de la vigilancia: los diagnosticos CIE-10 que registra el
// medico. Antes de esto, un brote de una enfermedad que se diagnostica por
// clinica y no por analitica era invisible.
//
// Coleccion aparte y no un campo mas en epidemiological_records: son dos
// fuentes con campos distintos, y meterlas juntas dejaria la mitad nulos en
// cada caso y obligaria a distinguirlas por un campo de tipo en cada consulta.
try {
    analytics.createCollection('diagnosis_records');
} catch (e) {
    print('diagnosis_records ya existia: ' + e.codeName);
}

// El _id es el id del diagnostico en medical_db, asi que la idempotencia la da
// la clave primaria y no hace falta un indice unico aparte: un reenvio de
// Kafka encuentra el documento y no lo duplica.

analytics.diagnosis_records.createIndex({ zona: 1, fecha: -1 });
analytics.diagnosis_records.createIndex({ codigoCie10: 1, fecha: -1 });
analytics.diagnosis_records.createIndex({ grupoEdad: 1, fecha: -1 });

// El indice de la deteccion de brotes clinicos: casos de un codigo, en una
// zona, dentro de una ventana de dias.
analytics.diagnosis_records.createIndex(
    { zona: 1, codigoCie10: 1, fecha: -1 },
    { name: 'deteccion_brotes_clinicos' });

// Las de notificacion obligatoria se consultan solas, para mandarlas al
// MINSA. Indice PARCIAL: son una fraccion minima de los diagnosticos.
analytics.diagnosis_records.createIndex(
    { fecha: -1 },
    { name: 'notificables',
      partialFilterExpression: { notificable: true } });

try {
    analytics.createCollection('outbreak_alerts');
} catch (e) {
    print('outbreak_alerts ya existia: ' + e.codeName);
}
analytics.outbreak_alerts.createIndex({ zona: 1, fechaDeteccion: -1 });
analytics.outbreak_alerts.createIndex({ estado: 1, nivelRiesgo: -1 });
// Una sola alerta ABIERTA por zona y codigo. Indice PARCIAL: al cerrarla, si
// el brote reaparece se puede abrir otra. Sin esto, cada caso nuevo del mismo
// brote abriria una alerta y el panel acabaria con veinte avisos del mismo
// hecho.
//
// La clave no incluye la fuente porque los dos espacios de codigos no se
// solapan: los del catalogo del laboratorio son HEM-001 y similares, y los
// CIE-10 son A09 o J11. El campo codigoPrueba lleva uno u otro segun la
// fuente de la alerta.
analytics.outbreak_alerts.createIndex(
    { zona: 1, codigoPrueba: 1 },
    { unique: true, name: 'alerta_abierta',
      partialFilterExpression: { estado: 'ABIERTA' } });

try {
    analytics.createCollection('indicator_snapshots');
} catch (e) {
    print('indicator_snapshots ya existia: ' + e.codeName);
}
analytics.indicator_snapshots.createIndex({ indicador: 1, periodo: -1 });

print('MedCore: lab_results_db y analytics_db inicializadas correctamente.');
