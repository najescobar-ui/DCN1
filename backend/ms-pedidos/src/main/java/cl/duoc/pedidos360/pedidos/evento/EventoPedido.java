package cl.duoc.pedidos360.pedidos.evento;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import cl.duoc.pedidos360.pedidos.model.EstadoPago;
import cl.duoc.pedidos360.pedidos.model.EstadoPedido;
import cl.duoc.pedidos360.pedidos.model.MetodoPago;
import cl.duoc.pedidos360.pedidos.model.Pedido;

/**
 * Fotografía de un pedido en el momento de un hecho de negocio. Es lo que viaja por RabbitMQ:
 * los consumidores no necesitan llamar a ms-pedidos para procesarlo.
 *
 * @param eventoId identificador único; los consumidores lo usan para no procesar dos veces el mismo mensaje
 */
public record EventoPedido(
        UUID eventoId,
        TipoEvento tipo,
        Long pedidoId,
        String clienteId,
        String clienteUsername,
        EstadoPedido estado,
        EstadoPago estadoPago,
        MetodoPago metodoPago,
        BigDecimal total,
        Instant ocurridoEn) {

    public static EventoPedido de(TipoEvento tipo, Pedido pedido) {
        return new EventoPedido(UUID.randomUUID(), tipo, pedido.getId(), pedido.getClienteId(),
                pedido.getClienteUsername(), pedido.getEstado(), pedido.getEstadoPago(), pedido.getMetodoPago(),
                pedido.getTotal(), Instant.now());
    }
}
