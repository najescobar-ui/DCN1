package cl.duoc.pedidos360.pedidos.evento;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import cl.duoc.pedidos360.pedidos.model.Pedido;

/**
 * Evento de dominio para el streaming (tópico pedidos.actividad, key = pedidoId). A diferencia de EventoPedido,
 * que avisa un hecho a RabbitMQ, este es una fotografía completa del pedido, con sus líneas: queda retenido en el
 * log y cualquier consumidor, presente o futuro, puede releerlo y reconstruir su propia vista.
 *
 * @param eventoId   identificador único; permite a los consumidores descartar duplicados
 * @param version    versión del esquema, para evolucionarlo sin romper a los consumidores
 * @param tipo       qué pasó
 * @param pedidoId   pedido afectado y key del mensaje: todos sus eventos van a la misma partición, en orden
 * @param clienteId  sub de Cognito del dueño (el seguimiento solo se muestra a él)
 * @param items      líneas del pedido, para las métricas por producto
 * @param ocurridoEn cuándo pasó (las métricas se agrupan por este tiempo, no por el de llegada)
 * @param origen     microservicio que lo publica
 */
public record ActividadPedido(
        UUID eventoId,
        int version,
        TipoEvento tipo,
        Long pedidoId,
        String clienteId,
        String clienteUsername,
        String estado,
        String estadoPago,
        String metodoPago,
        BigDecimal total,
        List<Item> items,
        Instant ocurridoEn,
        String origen) {

    public static final int VERSION = 1;

    public record Item(Long productoId, String nombre, int cantidad, BigDecimal precioUnitario) {
    }

    public static ActividadPedido de(TipoEvento tipo, Pedido pedido) {
        List<Item> items = pedido.getItems().stream()
                .map(i -> new Item(i.getProductoId(), i.getNombreProducto(), i.getCantidad(), i.getPrecioUnitario()))
                .toList();
        return new ActividadPedido(UUID.randomUUID(), VERSION, tipo, pedido.getId(), pedido.getClienteId(),
                pedido.getClienteUsername(), pedido.getEstado().name(), pedido.getEstadoPago().name(),
                pedido.getMetodoPago() == null ? null : pedido.getMetodoPago().name(), pedido.getTotal(), items,
                Instant.now(), "ms-pedidos");
    }
}
