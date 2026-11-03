package cl.duoc.pedidos360.notificaciones.notificacion;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Aviso enviado al cliente por un cambio en su pedido o pago. */
@Entity
@Table(name = "notificacion")
public class Notificacion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Evento que la originó; único para que un mensaje repetido no genere dos avisos. */
    @Column(nullable = false, unique = true)
    private UUID eventoId;

    @Column(nullable = false)
    private Long pedidoId;

    private String clienteId;

    private String clienteUsername;

    @Column(nullable = false, length = 30)
    private String tipo;

    @Column(nullable = false, length = 300)
    private String mensaje;

    @Column(nullable = false)
    private Instant creadaEn;

    protected Notificacion() {
    }

    public Notificacion(UUID eventoId, Long pedidoId, String clienteId, String clienteUsername, String tipo,
            String mensaje) {
        this.eventoId = eventoId;
        this.pedidoId = pedidoId;
        this.clienteId = clienteId;
        this.clienteUsername = clienteUsername;
        this.tipo = tipo;
        this.mensaje = mensaje;
        this.creadaEn = Instant.now();
    }

    public Long getId() { return id; }
    public UUID getEventoId() { return eventoId; }
    public Long getPedidoId() { return pedidoId; }
    public String getClienteId() { return clienteId; }
    public String getClienteUsername() { return clienteUsername; }
    public String getTipo() { return tipo; }
    public String getMensaje() { return mensaje; }
    public Instant getCreadaEn() { return creadaEn; }
}
