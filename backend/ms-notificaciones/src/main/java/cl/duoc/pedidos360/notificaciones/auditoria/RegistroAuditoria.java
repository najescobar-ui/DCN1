package cl.duoc.pedidos360.notificaciones.auditoria;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Una línea de la bitácora: qué evento pasó por el broker, con qué routing key y cuándo. */
@Entity
@Table(name = "registro_auditoria")
public class RegistroAuditoria {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private UUID eventoId;

    @Column(nullable = false, length = 100)
    private String routingKey;

    @Column(nullable = false, length = 30)
    private String tipo;

    @Column(nullable = false)
    private Long pedidoId;

    @Column(length = 20)
    private String estado;

    @Column(length = 20)
    private String estadoPago;

    private String clienteUsername;

    private Instant ocurridoEn;

    @Column(nullable = false)
    private Instant recibidoEn;

    protected RegistroAuditoria() {
    }

    public RegistroAuditoria(UUID eventoId, String routingKey, String tipo, Long pedidoId, String estado,
            String estadoPago, String clienteUsername, Instant ocurridoEn) {
        this.eventoId = eventoId;
        this.routingKey = routingKey;
        this.tipo = tipo;
        this.pedidoId = pedidoId;
        this.estado = estado;
        this.estadoPago = estadoPago;
        this.clienteUsername = clienteUsername;
        this.ocurridoEn = ocurridoEn;
        this.recibidoEn = Instant.now();
    }

    public Long getId() { return id; }
    public UUID getEventoId() { return eventoId; }
    public String getRoutingKey() { return routingKey; }
    public String getTipo() { return tipo; }
    public Long getPedidoId() { return pedidoId; }
    public String getEstado() { return estado; }
    public String getEstadoPago() { return estadoPago; }
    public String getClienteUsername() { return clienteUsername; }
    public Instant getOcurridoEn() { return ocurridoEn; }
    public Instant getRecibidoEn() { return recibidoEn; }
}
