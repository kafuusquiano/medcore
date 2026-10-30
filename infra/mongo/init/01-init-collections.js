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
analytics.epidemiological_records.createIndex({ zona: 1, fecha: -1 });
analytics.epidemiological_records.createIndex({ codigoCie10: 1, fecha: -1 });
analytics.epidemiological_records.createIndex({ zona: 1, codigoCie10: 1, fecha: -1 });

try {
    analytics.createCollection('outbreak_alerts');
} catch (e) {
    print('outbreak_alerts ya existia: ' + e.codeName);
}
analytics.outbreak_alerts.createIndex({ zona: 1, fechaDeteccion: -1 });
analytics.outbreak_alerts.createIndex({ estado: 1, nivelRiesgo: -1 });

try {
    analytics.createCollection('indicator_snapshots');
} catch (e) {
    print('indicator_snapshots ya existia: ' + e.codeName);
}
analytics.indicator_snapshots.createIndex({ indicador: 1, periodo: -1 });

print('MedCore: lab_results_db y analytics_db inicializadas correctamente.');
