package com.medcore.inventory.repository;

import com.medcore.inventory.domain.entity.Stock;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface StockRepository extends JpaRepository<Stock, Long> {

    Optional<Stock> findByReactivoIdAndLoteAndRetiradoFalse(Long idReactivo, String lote);

    List<Stock> findByReactivoIdAndRetiradoFalseOrderByFechaVencimientoAsc(Long idReactivo);

    Page<Stock> findByRetiradoFalseOrderByFechaVencimientoAsc(Pageable pageable);

    /**
     * Trae el lote para consumirlo, con bloqueo pesimista.
     *
     * <p>Dos tecnicos pueden descontar del mismo lote a la vez. El bloqueo
     * optimista de @Version lo detectaria, pero haria fallar al segundo con un
     * conflicto que para el no significa nada: el material esta ahi y solo
     * quiere apuntar que lo saco. Con el bloqueo pesimista el segundo espera
     * y su movimiento se aplica sobre la cantidad ya actualizada.
     *
     * <p>El timeout evita que una transaccion colgada deje el almacen
     * bloqueado; 5 segundos es mucho mas de lo que tarda este descuento.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@jakarta.persistence.QueryHint(name = "jakarta.persistence.lock.timeout", value = "5000"))
    @Query("SELECT s FROM Stock s WHERE s.id = :id")
    Optional<Stock> buscarParaMover(@Param("id") Long id);

    /**
     * Lote del que conviene consumir: el que caduca antes.
     *
     * <p>Los que no caducan van al final (NULLS LAST) para gastar primero lo
     * que tiene fecha. Se devuelve una lista y no un Optional porque quien
     * consume puede necesitar varios lotes si el primero no alcanza.
     */
    @Query("""
           SELECT s FROM Stock s
           WHERE s.reactivo.id = :idReactivo
             AND s.retirado = FALSE
             AND s.cantidadActual > 0
             AND (s.fechaVencimiento IS NULL OR s.fechaVencimiento >= CURRENT_DATE)
           ORDER BY s.fechaVencimiento ASC NULLS LAST, s.fechaIngreso ASC
           """)
    List<Stock> lotesConsumibles(@Param("idReactivo") Long idReactivo);

    /** Total disponible de un reactivo, descartando lo vencido y retirado. */
    @Query("""
           SELECT COALESCE(SUM(s.cantidadActual), 0) FROM Stock s
           WHERE s.reactivo.id = :idReactivo
             AND s.retirado = FALSE
             AND (s.fechaVencimiento IS NULL OR s.fechaVencimiento >= CURRENT_DATE)
           """)
    int totalDisponibleDe(@Param("idReactivo") Long idReactivo);

    /** Lotes ya vencidos que siguen en la estanteria. Los busca el scheduler. */
    @Query("""
           SELECT s FROM Stock s
           WHERE s.retirado = FALSE
             AND s.fechaVencimiento IS NOT NULL
             AND s.fechaVencimiento < CURRENT_DATE
           ORDER BY s.fechaVencimiento ASC
           """)
    List<Stock> lotesVencidos();

    /**
     * Lotes que vencen dentro del plazo de aviso y todavia no han vencido.
     *
     * <p>La fecha limite se pasa como parametro en lugar de calcularla en la
     * consulta: el plazo vive en la configuracion del servicio (MC3) y asi se
     * puede cambiar sin reconstruir la imagen.
     */
    @Query("""
           SELECT s FROM Stock s
           WHERE s.retirado = FALSE
             AND s.cantidadActual > 0
             AND s.fechaVencimiento IS NOT NULL
             AND s.fechaVencimiento >= CURRENT_DATE
             AND s.fechaVencimiento <= :limite
           ORDER BY s.fechaVencimiento ASC
           """)
    List<Stock> lotesPorVencer(@Param("limite") LocalDate limite);

    long countByRetiradoFalse();
}
