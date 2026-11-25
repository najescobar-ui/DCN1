package cl.duoc.pedidos360.analitica;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import cl.duoc.pedidos360.analitica.evento.ActividadPedido;
import cl.duoc.pedidos360.analitica.evento.TipoActividad;

/** Eventos de ejemplo para las pruebas. */
public final class Eventos {

    private Eventos() {
    }

    public static ActividadPedido creado(long pedidoId, String cliente, Instant cuando) {
        return new ActividadPedido(UUID.randomUUID(), 1, TipoActividad.PEDIDO_CREADO, pedidoId, cliente, cliente,
                "PENDIENTE", "PENDIENTE", "EFECTIVO", new BigDecimal("15980"),
                List.of(new ActividadPedido.Item(1L, "Pizza Napolitana", 2, new BigDecimal("6990")),
                        new ActividadPedido.Item(7L, "Bebida lata", 2, new BigDecimal("1000"))),
                cuando, "ms-pedidos");
    }

    public static ActividadPedido pago(long pedidoId, String cliente, Instant cuando) {
        return new ActividadPedido(UUID.randomUUID(), 1, TipoActividad.PAGO_APROBADO, pedidoId, cliente, cliente,
                "PENDIENTE", "PAGADO", "TARJETA", new BigDecimal("15980"), List.of(), cuando, "ms-pedidos");
    }

    public static ActividadPedido estado(long pedidoId, String cliente, String estado, Instant cuando) {
        return new ActividadPedido(UUID.randomUUID(), 1, TipoActividad.ESTADO_CAMBIADO, pedidoId, cliente, cliente,
                estado, "PAGADO", "TARJETA", new BigDecimal("15980"), List.of(), cuando, "ms-pedidos");
    }
}
