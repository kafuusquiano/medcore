package com.medcore.inventory.service;

import com.medcore.inventory.domain.dto.request.MovementRequest;
import com.medcore.inventory.domain.dto.response.MovementResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

/**
 * Movimientos de stock.
 *
 * <p>Es el unico camino por el que cambia la cantidad de un lote. Esa regla es
 * lo que garantiza que el stock actual y el historial nunca discrepen: no hay
 * ninguna operacion que toque la cantidad sin dejar su movimiento.
 */
public interface MovementService {

    /**
     * Registra un movimiento sobre un lote concreto.
     *
     * <p>Toma el lote con bloqueo pesimista: dos tecnicos pueden descontar del
     * mismo lote a la vez, y el segundo debe aplicar su movimiento sobre la
     * cantidad ya actualizada, no sobre la que leyo al empezar.
     */
    MovementResponse registrar(Long idStock, MovementRequest peticion,
                               String usuario, String requestId);

    /**
     * Consume una cantidad de un reactivo, repartiendola entre sus lotes.
     *
     * <p>Gasta primero el lote que caduca antes, y pasa al siguiente si no
     * alcanza. Es la operacion que usa el laboratorio al procesar una muestra:
     * pide "3 unidades de REA-HEM-01" sin tener que elegir el lote, que es
     * justo la decision que conviene que tome el sistema y no la persona.
     *
     * <p>Si el total disponible no alcanza, no consume nada: un consumo a
     * medias dejaria material apuntado como gastado sin haber podido hacer la
     * prueba.
     *
     * @return un movimiento por cada lote del que se tomo material
     */
    List<MovementResponse> consumir(Long idReactivo, int cantidad, String codigoMuestra,
                                    String usuario, String requestId);

    Page<MovementResponse> historialDeLote(Long idStock, Pageable pageable);

    Page<MovementResponse> historialDeReactivo(String codigoReactivo, Pageable pageable);

    Page<MovementResponse> historialCompleto(Pageable pageable);

    /** Trazabilidad inversa: que material consumio una muestra concreta. */
    List<MovementResponse> movimientosDeMuestra(String codigoMuestra);

    /** Consumo de un reactivo en los ultimos dias, para estimar la reposicion. */
    int consumoUltimosDias(String codigoReactivo, int dias);
}
