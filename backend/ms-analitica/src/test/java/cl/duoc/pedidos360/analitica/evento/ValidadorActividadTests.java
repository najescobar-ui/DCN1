package cl.duoc.pedidos360.analitica.evento;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import cl.duoc.pedidos360.analitica.Eventos;

class ValidadorActividadTests {

    @Test
    void aceptaUnEventoCompleto() {
        assertThatCode(() -> ValidadorActividad.validar(Eventos.creado(1, "c1", Instant.now()))).doesNotThrowAnyException();
    }

    @Test
    void rechazaEventoSinPedidoNiFechaConElDetalle() {
        ActividadPedido e = new ActividadPedido(UUID.randomUUID(), 1, TipoActividad.PAGO_APROBADO, null, "c", "c",
                null, null, null, BigDecimal.ONE, List.of(), null, "x");
        assertThatThrownBy(() -> ValidadorActividad.validar(e)).isInstanceOf(EventoInvalidoException.class)
                .hasMessageContaining("pedidoId").hasMessageContaining("ocurridoEn");
    }

    @Test
    void unPedidoCreadoSinItemsEsInvalido() {
        ActividadPedido e = new ActividadPedido(UUID.randomUUID(), 1, TipoActividad.PEDIDO_CREADO, 5L, "c", "c",
                "PENDIENTE", "PENDIENTE", "EFECTIVO", BigDecimal.TEN, List.of(), Instant.now(), "x");
        assertThatThrownBy(() -> ValidadorActividad.validar(e)).hasMessageContaining("items");
    }
}
