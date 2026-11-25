package cl.duoc.pedidos360.analitica.evento;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Evento del tópico pedidos.actividad (key = pedidoId). Es una fotografía completa del pedido en el momento del
 * hecho: los consumidores no necesitan llamar a ms-pedidos para procesarlo, y releer el tópico basta para
 * reconstruir cualquier proyección.
 *
 * @param eventoId        identificador único; los consumidores lo usan para no contar dos veces el mismo hecho
 * @param version         versión del esquema del evento, para poder evolucionarlo sin romper a los consumidores
 * @param tipo            qué pasó
 * @param pedidoId        pedido afectado (también es la key: todos sus eventos caen en la misma partición, en orden)
 * @param clienteId       sub de Cognito del dueño del pedido (el seguimiento solo se muestra a su dueño)
 * @param clienteUsername nombre visible del cliente
 * @param estado          estado del pedido después del hecho
 * @param estadoPago      estado del pago después del hecho
 * @param metodoPago      medio de pago elegido
 * @param total           total del pedido
 * @param items           líneas del pedido (para las métricas por producto)
 * @param ocurridoEn      cuándo pasó; las métricas se agrupan por este tiempo, no por el de llegada
 * @param origen          microservicio que lo publicó
 */
public record ActividadPedido(
        UUID eventoId,
        int version,
        TipoActividad tipo,
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

    /** Línea del pedido. */
    public record Item(Long productoId, String nombre, int cantidad, BigDecimal precioUnitario) {
    }
}
