// ============================================================
// MedCore — colecciones e indices de MongoDB.
// Se ejecuta una sola vez, al inicializar el volumen de Mongo.
//   lab_results_db  → laboratory-service (resultados crudos, tramas HL7)
//   analytics_db    → analytics-service (epidemiologia, brotes)
// ============================================================

// ---------- lab_results_db ----------
var lab = db.getSiblingDB('lab_results_db');

lab.createCollection('lab_results');
lab.lab_results.createIndex({ idMuestra: 1 }, { unique: true });
lab.lab_results.createIndex({ idPaciente: 1, fechaResultado: -1 });
lab.lab_results.createIndex({ codigoPrueba: 1, fechaResultado: -1 });
lab.lab_results.createIndex({ esCritico: 1, fechaResultado: -1 });

lab.createCollection('hl7_messages');
lab.hl7_messages.createIndex({ idMuestra: 1 });
lab.hl7_messages.createIndex({ fechaRecepcion: -1 });
lab.hl7_messages.createIndex({ tipoMensaje: 1, estadoProceso: 1 });

// ---------- analytics_db ----------
var analytics = db.getSiblingDB('analytics_db');

analytics.createCollection('epidemiological_records');
analytics.epidemiological_records.createIndex({ zona: 1, fecha: -1 });
analytics.epidemiological_records.createIndex({ codigoCie10: 1, fecha: -1 });
analytics.epidemiological_records.createIndex({ zona: 1, codigoCie10: 1, fecha: -1 });

analytics.createCollection('outbreak_alerts');
analytics.outbreak_alerts.createIndex({ zona: 1, fechaDeteccion: -1 });
analytics.outbreak_alerts.createIndex({ estado: 1, nivelRiesgo: -1 });

analytics.createCollection('indicator_snapshots');
analytics.indicator_snapshots.createIndex({ indicador: 1, periodo: -1 });

print('MedCore: lab_results_db y analytics_db inicializadas correctamente.');
