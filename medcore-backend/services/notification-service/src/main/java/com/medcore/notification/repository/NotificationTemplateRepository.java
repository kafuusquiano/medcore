package com.medcore.notification.repository;

import com.medcore.notification.domain.entity.NotificationTemplate;
import com.medcore.notification.domain.enums.NotificationType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Plantillas de los textos.
 *
 * <p>La clave primaria es el tipo de aviso: hay exactamente una por tipo, y un
 * identificador generado solo anadiria una indireccion.
 */
public interface NotificationTemplateRepository
        extends JpaRepository<NotificationTemplate, NotificationType> {

    List<NotificationTemplate> findByActivaTrueOrderByTipoAsc();
}
