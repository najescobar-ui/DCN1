package cl.duoc.pedidos360.analitica.proyeccion;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.Test;

import cl.duoc.pedidos360.analitica.Eventos;
import cl.duoc.pedidos360.analitica.config.StreamingProperties;
import cl.duoc.pedidos360.analitica.evento.ActividadPedido;
import cl.duoc.pedidos360.analitica.evento.MetricaVentas;

class AgregadorVentasTests {

    private static final Instant T0 = Instant.parse("2026-11-25T15:04:10Z");

    private final AgregadorVentas agregador = new AgregadorVentas(
            new StreamingProperties(null, null, 3, Duration.ofMinutes(1), 60, null));

    @Test
    void agrupaPorLaHoraDelHechoEnVentanasDeUnMinuto() {
        MetricaVentas m = agregador.registrar(Eventos.creado(1, "c1", T0)).orElseThrow();
        assertThat(m.ventana()).isEqualTo("2026-11-25T15:04Z");
        assertThat(m.inicio()).isEqualTo(Instant.parse("2026-11-25T15:04:00Z"));
        assertThat(m.fin()).isEqualTo(Instant.parse("2026-11-25T15:05:00Z"));
        assertThat(m.pedidosCreados()).isEqualTo(1);
        assertThat(m.unidadesVendidas()).isEqualTo(4);
        assertThat(m.porMetodoPago()).containsEntry("EFECTIVO", 1L);
    }

    @Test
    void pagosSumanMontoYTicketPromedio() {
        agregador.registrar(Eventos.pago(1, "c1", T0));
        MetricaVentas m = agregador.registrar(Eventos.pago(2, "c2", T0.plusSeconds(5))).orElseThrow();
        assertThat(m.pedidosPagados()).isEqualTo(2);
        assertThat(m.montoPagado()).isEqualByComparingTo("31960");
        assertThat(m.ticketPromedio()).isEqualByComparingTo(new BigDecimal("15980"));
    }

    @Test
    void elMismoEventoNoSeCuentaDosVeces() {
        ActividadPedido e = Eventos.creado(1, "c1", T0);
        assertThat(agregador.registrar(e)).isPresent();
        assertThat(agregador.registrar(e)).isEmpty();
        assertThat(agregador.eventosContados()).isEqualTo(1);
    }

    @Test
    void cancelacionesYTopProductos() {
        agregador.registrar(Eventos.creado(1, "c1", T0));
        agregador.registrar(Eventos.creado(2, "c2", T0));
        MetricaVentas m = agregador.registrar(Eventos.estado(1, "c1", "CANCELADO", T0)).orElseThrow();
        assertThat(m.pedidosCancelados()).isEqualTo(1);
        assertThat(m.topProductos()).first().satisfies(p -> {
            assertThat(p.nombre()).isEqualTo("Pizza Napolitana");
            assertThat(p.unidades()).isEqualTo(4);
            assertThat(p.monto()).isEqualByComparingTo("27960");
        });
    }

    @Test
    void eventosDeOtroMinutoVanAOtraVentana() {
        agregador.registrar(Eventos.creado(1, "c1", T0));
        MetricaVentas m = agregador.registrar(Eventos.creado(2, "c1", T0.plusSeconds(60))).orElseThrow();
        assertThat(m.ventana()).isEqualTo("2026-11-25T15:05Z");
        assertThat(m.pedidosCreados()).isEqualTo(1);
    }
}
