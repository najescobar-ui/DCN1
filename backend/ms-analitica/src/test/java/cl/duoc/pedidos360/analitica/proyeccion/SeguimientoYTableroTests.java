package cl.duoc.pedidos360.analitica.proyeccion;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import cl.duoc.pedidos360.analitica.Eventos;
import cl.duoc.pedidos360.analitica.config.StreamingProperties;
import cl.duoc.pedidos360.analitica.evento.MetricaVentas;
import cl.duoc.pedidos360.analitica.evento.TipoActividad;

class SeguimientoYTableroTests {

    private static final Instant T0 = Instant.parse("2026-11-25T15:04:10Z");

    @Test
    void laLineaDeTiempoQuedaOrdenadaPorLaHoraDelHechoConSuParticionYOffset() {
        SeguimientoPedidos s = new SeguimientoPedidos();
        s.registrar(Eventos.pago(10, "c1", T0.plusSeconds(30)), 1, 8);
        s.registrar(Eventos.creado(10, "c1", T0), 1, 7);
        SeguimientoPedidos.Vista v = s.de(10L).orElseThrow();
        assertThat(v.hitos()).extracting(SeguimientoPedidos.Hito::tipo)
                .containsExactly(TipoActividad.PEDIDO_CREADO, TipoActividad.PAGO_APROBADO);
        assertThat(v.estadoPago()).isEqualTo("PAGADO");
        assertThat(v.particion()).isEqualTo(1);
        assertThat(v.hitos().getFirst().offset()).isEqualTo(7);
    }

    @Test
    void cadaClienteVeSoloSusPedidosYElAdminTodos() {
        SeguimientoPedidos s = new SeguimientoPedidos();
        s.registrar(Eventos.creado(1, "c1", T0), 0, 0);
        s.registrar(Eventos.creado(2, "c2", T0), 1, 0);
        s.registrar(Eventos.creado(3, "c1", T0), 2, 0);
        assertThat(s.recientes("c1", 10)).extracting(SeguimientoPedidos.Vista::pedidoId).containsExactly(3L, 1L);
        assertThat(s.recientes(null, 10)).hasSize(3);
    }

    @Test
    void elTableroSeQuedaConLaVersionMasNuevaDeCadaVentanaYSumaLaHora() {
        TableroVentas t = new TableroVentas(new StreamingProperties(null, null, 3, Duration.ofMinutes(1), 60, null));
        t.registrar(metrica("2026-11-25T15:04Z", 2, 1, "10000", T0.plusSeconds(2)));
        t.registrar(metrica("2026-11-25T15:04Z", 1, 0, "0", T0.plusSeconds(1))); // versión vieja, se ignora
        t.registrar(metrica("2026-11-25T15:05Z", 1, 1, "20000", T0.plusSeconds(61)));
        TableroVentas.Resumen r = t.resumen();
        assertThat(r.ventanas()).hasSize(2);
        assertThat(r.pedidosCreados()).isEqualTo(3);
        assertThat(r.pedidosPagados()).isEqualTo(2);
        assertThat(r.montoPagado()).isEqualByComparingTo("30000");
        assertThat(r.ticketPromedio()).isEqualByComparingTo("15000");
    }

    private static MetricaVentas metrica(String ventana, long creados, long pagados, String monto, Instant generado) {
        return new MetricaVentas(ventana, T0, T0.plusSeconds(60), creados, pagados, 0, new BigDecimal(monto),
                BigDecimal.ZERO, creados, List.of(), Map.of("EFECTIVO", creados), creados + pagados, generado);
    }
}
