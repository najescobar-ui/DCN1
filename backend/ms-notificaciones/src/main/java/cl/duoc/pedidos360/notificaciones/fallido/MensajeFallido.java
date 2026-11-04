package cl.duoc.pedidos360.notificaciones.fallido;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Mensaje no entregado que llegó a una DLQ. Lo registra el listener de la DLQ con el motivo que informa
 * RabbitMQ en el header x-death, y guarda lo necesario para reprocesarlo (exchange, routing key y cuerpo).
 */
@Entity
@Table(name = "mensaje_fallido")
public class MensajeFallido {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** DLQ donde se recibió (notificaciones.pedidos.dlq). */
    @Column(nullable = false, length = 120)
    private String dlq;

    /** Cola donde falló el mensaje (notificaciones.pedidos). */
    @Column(length = 120)
    private String colaOrigen;

    /** Motivo según RabbitMQ: rejected (NACK), expired (TTL), maxlen (largo máximo) o delivery_limit. */
    @Column(length = 30)
    private String motivo;

    /** Veces que el mensaje murió por ese motivo en esa cola. */
    private long veces;

    @Column(length = 120)
    private String exchangeOrigen;

    @Column(length = 255)
    private String routingKeyOrigen;

    @Column(length = 64)
    private String messageId;

    @Column(length = 100)
    private String contentType;

    /** Headers del mensaje original, sin los que agrega RabbitMQ al morir (JSON). */
    @Column(length = 4000)
    private String headers;

    @Column(length = 10000)
    private String cuerpo;

    @Column(nullable = false)
    private Instant recibidoEn;

    private Instant reprocesadoEn;

    protected MensajeFallido() {
    }

    public MensajeFallido(String dlq, String colaOrigen, String motivo, long veces, String exchangeOrigen,
            String routingKeyOrigen, String messageId, String contentType, String headers, String cuerpo) {
        this.dlq = dlq;
        this.colaOrigen = colaOrigen;
        this.motivo = motivo;
        this.veces = veces;
        this.exchangeOrigen = exchangeOrigen;
        this.routingKeyOrigen = routingKeyOrigen;
        this.messageId = messageId;
        this.contentType = contentType;
        this.headers = headers;
        this.cuerpo = cuerpo;
        this.recibidoEn = Instant.now();
    }

    public void marcarReprocesado() {
        this.reprocesadoEn = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getDlq() {
        return dlq;
    }

    public String getColaOrigen() {
        return colaOrigen;
    }

    public String getMotivo() {
        return motivo;
    }

    public long getVeces() {
        return veces;
    }

    public String getExchangeOrigen() {
        return exchangeOrigen;
    }

    public String getRoutingKeyOrigen() {
        return routingKeyOrigen;
    }

    public String getMessageId() {
        return messageId;
    }

    public String getContentType() {
        return contentType;
    }

    public String getHeaders() {
        return headers;
    }

    public String getCuerpo() {
        return cuerpo;
    }

    public Instant getRecibidoEn() {
        return recibidoEn;
    }

    public Instant getReprocesadoEn() {
        return reprocesadoEn;
    }
}
