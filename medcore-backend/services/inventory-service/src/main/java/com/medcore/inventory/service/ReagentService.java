package com.medcore.inventory.service;

import com.medcore.inventory.domain.dto.request.ReagentRequest;
import com.medcore.inventory.domain.dto.response.ReagentResponse;
import com.medcore.inventory.domain.entity.Reagent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

/** Catalogo de reactivos e insumos. */
public interface ReagentService {

    ReagentResponse crear(ReagentRequest peticion, String requestId);

    /**
     * Actualiza los datos administrativos.
     *
     * <p>El codigo no se puede cambiar: esta rotulado en la estanteria y en
     * los movimientos ya registrados.
     */
    ReagentResponse actualizar(Long id, ReagentRequest peticion, String requestId);

    /**
     * Da de baja el reactivo sin borrarlo.
     *
     * <p>Sus movimientos historicos tienen que seguir siendo legibles, y los
     * lotes que queden en la estanteria tambien: la baja impide registrar
     * material nuevo, no consultar lo que ya hay.
     */
    ReagentResponse desactivar(Long id, String requestId);

    ReagentResponse reactivar(Long id, String requestId);

    /** Ficha completa: el reactivo con sus lotes y su total disponible. */
    ReagentResponse obtenerPorId(Long id);

    ReagentResponse obtenerPorCodigo(String codigo);

    /** Busca la entidad sin convertirla, para uso interno del servicio. */
    Reagent buscarEntidad(Long id);

    Page<ReagentResponse> listarActivos(Pageable pageable);

    List<ReagentResponse> listarPorCategoria(String categoria);

    List<ReagentResponse> buscar(String texto);

    List<String> categorias();

    /**
     * Reactivos cuyo total disponible esta por debajo de su minimo.
     *
     * <p>Es la lista que el responsable del almacen usa para hacer el pedido,
     * con la cantidad sugerida ya calculada.
     */
    List<ReagentResponse> bajoMinimo();
}
