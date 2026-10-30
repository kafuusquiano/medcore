package com.medcore.inventory.service;

import com.medcore.inventory.domain.dto.request.StockEntryRequest;
import com.medcore.inventory.domain.dto.response.StockResponse;
import com.medcore.inventory.domain.entity.Stock;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

/** Lotes en almacen. */
public interface StockService {

    /**
     * Registra la entrada de un lote nuevo.
     *
     * <p>Deja tambien su movimiento de ENTRADA: la cantidad de un lote nunca
     * cambia sin un movimiento que lo justifique, ni siquiera al crearlo.
     */
    StockResponse ingresar(StockEntryRequest peticion, String usuario, String requestId);

    /**
     * Retira el lote del almacen.
     *
     * <p>No pone la cantidad a cero: para eso se registra una MERMA, que es la
     * que deja constancia de cuantas unidades se perdieron. Separarlo permite
     * saber si un lote se retiro lleno o ya casi gastado.
     */
    StockResponse retirar(Long id, String motivo, String requestId);

    StockResponse reubicar(Long id, String ubicacion, String requestId);

    StockResponse obtenerPorId(Long id);

    /** Busca la entidad sin convertirla, para uso interno del servicio. */
    Stock buscarEntidad(Long id);

    Page<StockResponse> listarVigentes(Pageable pageable);

    List<StockResponse> lotesDeReactivo(Long idReactivo);

    /**
     * Lotes de los que conviene consumir, en orden de vencimiento.
     *
     * <p>El que caduca antes va primero, y los que no caducan al final: asi se
     * gasta lo que tiene fecha mientras sirve.
     */
    List<StockResponse> lotesConsumibles(Long idReactivo);

    /** Lotes ya vencidos que siguen en la estanteria. */
    List<StockResponse> vencidos();

    /** Lotes que vencen dentro del plazo de aviso configurado. */
    List<StockResponse> porVencer();

    int totalDisponibleDe(Long idReactivo);
}
