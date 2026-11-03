package cl.duoc.pedidos360.notificaciones.comprobante;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Comprobante de pago emitido de forma asíncrona cuando un pedido queda pagado. */
@Entity
@Table(name = "comprobante")
public class Comprobante {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Folio visible para el cliente (C-000012). */
    @Column(nullable = false, unique = true, length = 20)
    private String folio;

    /** Un pedido tiene un solo comprobante: el comando repetido no emite otro. */
    @Column(nullable = false, unique = true)
    private Long pedidoId;

    @Column(nullable = false)
    private UUID eventoId;

    private String clienteId;

    private String clienteUsername;

    @Column(length = 20)
    private String metodoPago;

    @Column(precision = 12, scale = 2)
    private BigDecimal total;

    @Column(nullable = false)
    private Instant emitidoEn;

    protected Comprobante() {
    }

    public Comprobante(String folio, Long pedidoId, UUID eventoId, String clienteId, String clienteUsername,
            String metodoPago, BigDecimal total) {
        this.folio = folio;
        this.pedidoId = pedidoId;
        this.eventoId = eventoId;
        this.clienteId = clienteId;
        this.clienteUsername = clienteUsername;
        this.metodoPago = metodoPago;
        this.total = total;
        this.emitidoEn = Instant.now();
    }

    public Long getId() { return id; }
    public String getFolio() { return folio; }
    public Long getPedidoId() { return pedidoId; }
    public UUID getEventoId() { return eventoId; }
    public String getClienteId() { return clienteId; }
    public String getClienteUsername() { return clienteUsername; }
    public String getMetodoPago() { return metodoPago; }
    public BigDecimal getTotal() { return total; }
    public Instant getEmitidoEn() { return emitidoEn; }
}
