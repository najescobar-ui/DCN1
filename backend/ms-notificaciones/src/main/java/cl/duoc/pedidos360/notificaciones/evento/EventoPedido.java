package cl.duoc.pedidos360.notificaciones.evento;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Contrato del mensaje que publica ms-pedidos. Los estados viajan como texto para que un estado nuevo
 * en ms-pedidos no rompa la lectura aquí.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record EventoPedido(
        UUID eventoId,
        TipoEvento tipo,
        Long pedidoId,
        String clienteId,
        String clienteUsername,
        String estado,
        String estadoPago,
        String metodoPago,
        BigDecimal total,
        Instant ocurridoEn) {

    /** Número de pedido tal como lo ve el cliente en el sitio. */
    public String numeroPedido() {
        return "#P360-%05d".formatted(pedidoId);
    }
}
