package cl.duoc.pedidos360.analitica.proyeccion;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.springframework.stereotype.Service;

import cl.duoc.pedidos360.analitica.config.StreamingProperties;
import cl.duoc.pedidos360.analitica.evento.MetricaVentas;
import cl.duoc.pedidos360.analitica.evento.MetricaVentas.ProductoVendido;

/**
 * Caso de uso 3 · tablero del administrador. Guarda la última versión de cada ventana que llega por
 * ventas.metricas (igual que la compactación del tópico) y arma el resumen de la última hora.
 */
@Service
public class TableroVentas {

    private final int maxVentanas;
    private final TreeMap<String, MetricaVentas> ventanas = new TreeMap<>();
    private Instant ultimaActualizacion;

    public TableroVentas(StreamingProperties props) {
        this.maxVentanas = props.ventanasEnTablero();
    }

    public synchronized void registrar(MetricaVentas m) {
        MetricaVentas actual = ventanas.get(m.ventana());
        // Si llega una versión más vieja de la misma ventana (relectura del log), se queda la más nueva.
        if (actual == null || !m.generadoEn().isBefore(actual.generadoEn())) {
            ventanas.put(m.ventana(), m);
        }
        while (ventanas.size() > maxVentanas) {
            ventanas.pollFirstEntry();
        }
        ultimaActualizacion = Instant.now();
    }

    public synchronized Resumen resumen() {
        long creados = 0;
        long pagados = 0;
        long cancelados = 0;
        long unidades = 0;
        BigDecimal monto = BigDecimal.ZERO;
        Map<Long, ProductoVendido> productos = new HashMap<>();
        Map<String, Long> porMetodo = new TreeMap<>();
        for (MetricaVentas m : ventanas.values()) {
            creados += m.pedidosCreados();
            pagados += m.pedidosPagados();
            cancelados += m.pedidosCancelados();
            unidades += m.unidadesVendidas();
            monto = monto.add(m.montoPagado());
            m.porMetodoPago().forEach((k, v) -> porMetodo.merge(k, v, Long::sum));
            for (ProductoVendido p : m.topProductos()) {
                productos.merge(p.productoId(), p, (a, b) -> new ProductoVendido(a.productoId(), a.nombre(),
                        a.unidades() + b.unidades(), a.monto().add(b.monto())));
            }
        }
        BigDecimal ticket = pagados == 0 ? BigDecimal.ZERO
                : monto.divide(BigDecimal.valueOf(pagados), 0, RoundingMode.HALF_UP);
        List<ProductoVendido> top = productos.values().stream()
                .sorted(Comparator.comparingLong(ProductoVendido::unidades).reversed())
                .limit(5).toList();
        return new Resumen(creados, pagados, cancelados, unidades, monto, ticket, top, porMetodo,
                new ArrayList<>(ventanas.values()), ultimaActualizacion);
    }

    /**
     * @param ventanas ventanas de la última hora, de la más antigua a la más reciente
     */
    public record Resumen(long pedidosCreados, long pedidosPagados, long pedidosCancelados, long unidadesVendidas,
            BigDecimal montoPagado, BigDecimal ticketPromedio, List<ProductoVendido> topProductos,
            Map<String, Long> porMetodoPago, List<MetricaVentas> ventanas, Instant actualizadoEn) {
    }
}
