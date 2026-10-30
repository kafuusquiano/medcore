package com.medcore.laboratory.repository.mongo;

import com.medcore.laboratory.domain.document.ResultDocument;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

/**
 * Repositorio de los resultados crudos en MongoDB.
 *
 * <p>Guarda la trama original del equipo, cuya forma varia segun el
 * fabricante. Permite reprocesar un resultado si manana se descubre que el
 * parser interpretaba mal un campo.
 */
public interface ResultDocumentRepository extends MongoRepository<ResultDocument, String> {

    /**
     * Busca el documento de un mensaje concreto.
     *
     * <p>Es la clave unica de la coleccion. Sirve para reconocer el reenvio de
     * un equipo que no recibio el ACK a tiempo: vuelve con el mismo control
     * id, y entonces se reutiliza el documento en lugar de insertar otro, que
     * chocaria con el indice unico.
     */
    Optional<ResultDocument> findByIdMuestraAndIdMensaje(String idMuestra, String idMensaje);

    /** Todos los mensajes recibidos de una muestra, del mas reciente al mas antiguo. */
    List<ResultDocument> findByIdMuestraOrderByFechaRecepcionDesc(String idMuestra);

    Optional<ResultDocument> findByCodigoMuestra(String codigoMuestra);
}
