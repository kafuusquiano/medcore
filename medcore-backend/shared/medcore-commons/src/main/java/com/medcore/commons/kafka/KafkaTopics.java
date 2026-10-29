package com.medcore.commons.kafka;

import java.util.List;

/**
 * Nombres de los nueve topicos Kafka del sistema.
 *
 * <p>Productores y consumidores referencian estas constantes en lugar de
 * literales, para que un cambio de nombre no deje a un consumidor escuchando
 * un topico que ya nadie produce.
 *
 * <p>Los topicos se crean con {@code infra/kafka/create-topics.sh}.
 */
public final class KafkaTopics {

    /** Produce patient-service. Consumen notification-service y appointment-service. */
    public static final String PATIENT_CREATED = "patient.created";

    /** Produce appointment-service. Consume notification-service. */
    public static final String APPOINTMENT_CREATED = "appointment.created";

    /** Produce appointment-service. Consumen notification-service y medical-service. */
    public static final String APPOINTMENT_CANCELLED = "appointment.cancelled";

    /** Produce medical-service. Consumen laboratory-service y notification-service. */
    public static final String LAB_ORDER_CREATED = "lab-order.created";

    /** Produce laboratory-service. Solo se registra en el log interno. */
    public static final String SAMPLE_REGISTERED = "sample.registered";

    /** Produce laboratory-service. Consumen analytics-service y notification-service. */
    public static final String RESULT_REGISTERED = "result.registered";

    /** Produce laboratory-service. Consumen notification-service y analytics-service. */
    public static final String RESULT_CRITICAL = "result.critical";

    /** Produce inventory-service. Consume notification-service. */
    public static final String STOCK_LOW = "stock.low";

    /** Produce inventory-service. Consume notification-service. */
    public static final String STOCK_EXPIRED = "stock.expired";

    /** Los nueve topicos, en el mismo orden que create-topics.sh. */
    public static final List<String> TODOS = List.of(
            PATIENT_CREATED,
            APPOINTMENT_CREATED,
            APPOINTMENT_CANCELLED,
            LAB_ORDER_CREATED,
            SAMPLE_REGISTERED,
            RESULT_REGISTERED,
            RESULT_CRITICAL,
            STOCK_LOW,
            STOCK_EXPIRED
    );

    private KafkaTopics() {
        throw new AssertionError("Clase de constantes, no instanciable");
    }
}
