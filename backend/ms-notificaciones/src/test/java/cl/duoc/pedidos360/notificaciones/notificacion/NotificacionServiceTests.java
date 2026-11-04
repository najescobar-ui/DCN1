package cl.duoc.pedidos360.notificaciones.notificacion;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import cl.duoc.pedidos360.notificaciones.evento.EventoPedido;
import cl.duoc.pedidos360.notificaciones.evento.TipoEvento;

class NotificacionServiceTests {

    private static EventoPedido evento(TipoEvento tipo, String estado) {
        return new EventoPedido(UUID.randomUUID(), tipo, 12L, "sub-1", "cliente1", estado, "PENDIENTE", "TARJETA",
                new BigDecimal("12990"), Instant.now());
    }

    @Test
    void mensajesSegunTipoYEstado() {
        assertThat(NotificacionService.mensajePara(evento(TipoEvento.PEDIDO_CREADO, "PENDIENTE")))
                .startsWith("Recibimos tu pedido #P360-00012");
        assertThat(NotificacionService.mensajePara(evento(TipoEvento.ESTADO_CAMBIADO, "DESPACHADO")))
                .isEqualTo("Tu pedido #P360-00012 va en camino.");
        assertThat(NotificacionService.mensajePara(evento(TipoEvento.PAGO_RECHAZADO, "PENDIENTE")))
                .contains("no se completó");
    }
}
