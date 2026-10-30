package com.medcore.notification.domain.entity;

import com.medcore.notification.domain.enums.NotificationChannel;
import com.medcore.notification.domain.enums.NotificationStatus;
import com.medcore.notification.domain.enums.NotificationType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.Objects;

/**
 * Un aviso, por un canal concreto.
 *
 * <p>Un mismo hecho produce varias filas: el aviso de cita reservada va por
 * correo y tambien in-app, y cada uno se envia, falla y se reintenta por
 * separado. Guardarlos como una sola fila con varios canales obligaria a
 * llevar un estado por canal dentro del mismo registro, y entonces "esta
 * enviada" dejaria de tener una respuesta.
 *
 * <p>No se borran. Un aviso que no llego es justamente el que hay que poder
 * consultar: si el correo de un valor critico fallo, alguien tiene que
 * enterarse y llamar por telefono.
 */
@Entity
@Table(name = "notifications")
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private NotificationType tipo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private NotificationChannel canal;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private NotificationStatus estado = NotificationStatus.PENDIENTE;

    /**
     * Identificador del evento que lo origino.
     *
     * <p>Junto con el tipo y el canal forma la clave de idempotencia: Kafka
     * entrega al menos una vez, y sin esto un reenvio mandaria el mismo correo
     * dos veces al paciente.
     */
    @Column(name = "event_id", nullable = false, length = 60)
    private String eventId;

    /**
     * A quien se avisa. En correo es la direccion; en in-app, el
     * identificador del usuario o del paciente.
     */
    @Column(nullable = false, length = 150)
    private String destinatario;

    /** Paciente al que se refiere, si aplica. Permite listar sus avisos. */
    @Column(name = "id_paciente")
    private Long idPaciente;

    /** Usuario interno al que se dirige, si es un aviso del personal. */
    @Column(name = "id_usuario")
    private Long idUsuario;

    @Column(nullable = false, length = 200)
    private String asunto;

    /**
     * Texto del aviso. En in-app es lo que se muestra; en correo, la version
     * en texto plano, porque el HTML se arma con la plantilla al enviar y no
     * se guarda: ocuparia mucho y se puede reconstruir.
     */
    @Column(nullable = false, length = 1000)
    private String mensaje;

    /** Ruta del frontend a la que lleva el aviso al pulsarlo. */
    @Column(name = "enlace", length = 300)
    private String enlace;

    /**
     * Datos del evento en JSON, para rellenar la plantilla HTML del correo.
     *
     * <p>Se guardan porque un reintento puede ocurrir horas despues, incluso
     * tras reiniciar el servicio, y entonces el consumidor que los tenia en
     * memoria ya no existe. Sin esto, el correo reintentado saldria con los
     * huecos vacios.
     *
     * <p>Como texto y no como JSONB: no se consulta por su contenido, solo se
     * lee entero al enviar, y asi la entidad no depende de un tipo propio de
     * Postgres.
     */
    @Column(name = "datos", columnDefinition = "text")
    private String datos;

    @Column(name = "intentos", nullable = false)
    private int intentos = 0;

    /** Por que fallo o por que se omitio. Nulo si se envio bien. */
    @Column(name = "motivo_fallo", length = 500)
    private String motivoFallo;

    /**
     * Cuando debe salir. Normalmente ahora mismo; el recordatorio de cita se
     * programa para el dia anterior.
     */
    @Column(name = "fecha_programada", nullable = false)
    private Instant fechaProgramada;

    @Column(name = "fecha_envio")
    private Instant fechaEnvio;

    @Column(name = "fecha_lectura")
    private Instant fechaLectura;

    @Column(name = "fecha_creacion", nullable = false, updatable = false)
    private Instant fechaCreacion;

    @Column(name = "fecha_actualizacion", nullable = false)
    private Instant fechaActualizacion;

    /**
     * Bloqueo optimista. El consumidor y el reintentador pueden tocar el mismo
     * aviso a la vez; el segundo recibe un conflicto en lugar de enviarlo dos
     * veces.
     */
    @Version
    private Long version;

    protected Notification() {
        // Requerido por JPA.
    }

    public Notification(NotificationType tipo, NotificationChannel canal, String eventId,
                        String destinatario, Long idPaciente, Long idUsuario,
                        String asunto, String mensaje, String enlace, String datos,
                        Instant fechaProgramada) {
        this.tipo = tipo;
        this.canal = canal;
        this.eventId = eventId;
        this.destinatario = destinatario;
        this.idPaciente = idPaciente;
        this.idUsuario = idUsuario;
        this.asunto = asunto;
        this.mensaje = mensaje;
        this.enlace = enlace;
        this.datos = datos;
        this.fechaProgramada = fechaProgramada == null ? Instant.now() : fechaProgramada;
        this.estado = NotificationStatus.PENDIENTE;
    }

    @PrePersist
    void alCrear() {
        Instant ahora = Instant.now();
        this.fechaCreacion = ahora;
        this.fechaActualizacion = ahora;
        if (this.fechaProgramada == null) {
            this.fechaProgramada = ahora;
        }
    }

    @PreUpdate
    void alActualizar() {
        this.fechaActualizacion = Instant.now();
    }

    // ---- Comportamiento de dominio -----------------------------------------

    /** El canal acepto el envio. */
    public void marcarEnviada() {
        exigirTransicion(NotificationStatus.ENVIADA);
        this.estado = NotificationStatus.ENVIADA;
        this.fechaEnvio = Instant.now();
        this.motivoFallo = null;
        this.intentos++;
    }

    /**
     * El canal rechazo el envio.
     *
     * <p>Cuenta el intento y conserva el motivo. Que el aviso quede FALLIDA y
     * no se borre es lo que permite saber que no llego.
     */
    public void marcarFallida(String motivo) {
        exigirTransicion(NotificationStatus.FALLIDA);
        this.estado = NotificationStatus.FALLIDA;
        this.motivoFallo = recortar(motivo);
        this.intentos++;
    }

    /**
     * No se intento enviar: el canal no esta implementado o faltaba el dato.
     *
     * <p>No cuenta como intento, porque no hay nada que reintentar.
     */
    public void marcarOmitida(String motivo) {
        exigirTransicion(NotificationStatus.OMITIDA);
        this.estado = NotificationStatus.OMITIDA;
        this.motivoFallo = recortar(motivo);
    }

    /** El usuario abrio el aviso. Solo tiene sentido en los in-app. */
    public void marcarLeida() {
        if (estado == NotificationStatus.LEIDA) {
            // Volver a abrir un aviso ya leido no es un error; se conserva la
            // primera lectura, que es la que dice cuando se entero.
            return;
        }
        exigirTransicion(NotificationStatus.LEIDA);
        this.estado = NotificationStatus.LEIDA;
        this.fechaLectura = Instant.now();
    }

    /**
     * Indica si queda algun intento.
     *
     * <p>El tope viene de la configuracion y no de la entidad: ajustarlo no
     * deberia exigir recompilar.
     */
    public boolean puedeReintentarse(int maxIntentos) {
        return estado.admiteReintento() && intentos < maxIntentos;
    }

    public boolean estaLista(Instant ahora) {
        return !fechaProgramada.isAfter(ahora);
    }

    private void exigirTransicion(NotificationStatus destino) {
        if (!estado.puedePasarA(destino)) {
            throw new IllegalStateException(
                    "Un aviso " + estado + " no puede pasar a " + destino + ".");
        }
    }

    /** El motivo cabe en 500 caracteres; una traza larga se recorta. */
    private String recortar(String motivo) {
        if (motivo == null) {
            return null;
        }
        return motivo.length() > 500 ? motivo.substring(0, 500) : motivo;
    }

    public Long getId() {
        return id;
    }

    public NotificationType getTipo() {
        return tipo;
    }

    public NotificationChannel getCanal() {
        return canal;
    }

    public NotificationStatus getEstado() {
        return estado;
    }

    public String getEventId() {
        return eventId;
    }

    public String getDestinatario() {
        return destinatario;
    }

    public Long getIdPaciente() {
        return idPaciente;
    }

    public Long getIdUsuario() {
        return idUsuario;
    }

    public String getAsunto() {
        return asunto;
    }

    public String getMensaje() {
        return mensaje;
    }

    public String getEnlace() {
        return enlace;
    }

    public String getDatos() {
        return datos;
    }

    public int getIntentos() {
        return intentos;
    }

    public String getMotivoFallo() {
        return motivoFallo;
    }

    public Instant getFechaProgramada() {
        return fechaProgramada;
    }

    public Instant getFechaEnvio() {
        return fechaEnvio;
    }

    public Instant getFechaLectura() {
        return fechaLectura;
    }

    public Instant getFechaCreacion() {
        return fechaCreacion;
    }

    public Instant getFechaActualizacion() {
        return fechaActualizacion;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Notification otra)) {
            return false;
        }
        return id != null && id.equals(otra.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }

    @Override
    public String toString() {
        return "Notification{" + tipo + " por " + canal + " a " + destinatario
                + " [" + estado + "]}";
    }
}
