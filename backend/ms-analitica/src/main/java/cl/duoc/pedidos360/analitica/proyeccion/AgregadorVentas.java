package cl.duoc.pedidos360.analitica.proyeccion;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.TreeMap;

import org.springframework.stereotype.Service;

import cl.duoc.pedidos360.analitica.config.StreamingProperties;
import cl.duoc.pedidos360.analitica.evento.ActividadPedido;
import cl.duoc.pedidos360.analitica.evento.MetricaVentas;
import cl.duoc.pedidos360.analitica.evento.MetricaVentas.ProductoVendido;

/**
 * Caso de uso 1 · analítica de ventas en vivo. Acumula los eventos de actividad en ventanas de tiempo (según
 * cuándo ocurrió el hecho, no cuándo llegó) y entrega la métrica actualizada de la ventana afectada.
 *
 * Es la lógica de dominio del grupo analitica-ventas: no sabe nada de Kafka. Los consumidores de esta instancia
 * (uno por partición) la comparten, por eso los métodos son synchronized.
 */
@Service
public class AgregadorVentas {

    private static final int VENTANAS_MAXIMAS = 24 * 60;
    private static final int TOP = 5;

    private final long ventanaMs;
    private final NavigableMap<Instant, Acumulado> ventanas = new TreeMap<>();
    private final EventosVistos vistos = new EventosVistos(200_000);

    public AgregadorVentas(StreamingProperties props) {
        this.ventanaMs = props.ventana().toMillis();
    }

    /**
     * Suma el evento a su ventana.
     *
     * @return la métrica recalculada de esa ventana, o vacío si el evento ya se había contado
     */
    public synchronized Optional<MetricaVentas> registrar(ActividadPedido e) {
        if (!vistos.registrar(e.eventoId())) {
            return Optional.empty();
        }
        Instant inicio = inicioDeVentana(e.ocurridoEn());
        Acumulado acc = ventanas.computeIfAbsent(inicio, i -> new Acumulado());
        acc.eventos++;
        switch (e.tipo()) {
            case PEDIDO_CREADO -> {
                acc.creados++;
                acc.porMetodoPago.merge(e.metodoPago() == null ? "SIN_DATO" : e.metodoPago(), 1L, Long::sum);
                for (ActividadPedido.Item item : e.items()) {
                    acc.unidades += item.cantidad();
                    ProductoAcumulado p = acc.productos.computeIfAbsent(item.productoId(),
                            id -> new ProductoAcumulado(id, item.nombre()));
                    p.unidades += item.cantidad();
                    p.monto = p.monto.add(item.precioUnitario().multiply(BigDecimal.valueOf(item.cantidad())));
                }
            }
            case PAGO_APROBADO -> {
                acc.pagados++;
                acc.montoPagado = acc.montoPagado.add(e.total() == null ? BigDecimal.ZERO : e.total());
            }
            case ESTADO_CAMBIADO -> {
                if ("CANCELADO".equals(e.estado())) {
                    acc.cancelados++;
                }
            }
            case PAGO_RECHAZADO -> {
                // Cuenta como evento procesado; no suma ventas.
            }
        }
        while (ventanas.size() > VENTANAS_MAXIMAS) {
            ventanas.pollFirstEntry();
        }
        return Optional.of(metrica(inicio, acc));
    }

    public synchronized int eventosContados() {
        return vistos.cantidad();
    }

    Instant inicioDeVentana(Instant t) {
        long ms = t.toEpochMilli();
        return Instant.ofEpochMilli(ms - Math.floorMod(ms, ventanaMs));
    }

    private MetricaVentas metrica(Instant inicio, Acumulado acc) {
        BigDecimal ticket = acc.pagados == 0 ? BigDecimal.ZERO
                : acc.montoPagado.divide(BigDecimal.valueOf(acc.pagados), 0, RoundingMode.HALF_UP);
        List<ProductoVendido> top = acc.productos.values().stream()
                .sorted(Comparator.comparingLong((ProductoAcumulado p) -> p.unidades).reversed()
                        .thenComparing(p -> p.productoId))
                .limit(TOP)
                .map(p -> new ProductoVendido(p.productoId, p.nombre, p.unidades, p.monto))
                .toList();
        return new MetricaVentas(idDeVentana(inicio), inicio, inicio.plusMillis(ventanaMs), acc.creados, acc.pagados,
                acc.cancelados, acc.montoPagado, ticket, acc.unidades, top, Map.copyOf(acc.porMetodoPago), acc.eventos,
                Instant.now());
    }

    /** Id legible y ordenable de la ventana; es la key del mensaje en ventas.metricas. */
    static String idDeVentana(Instant inicio) {
        return inicio.truncatedTo(ChronoUnit.SECONDS).toString().replace(":00Z", "Z");
    }

    private static final class Acumulado {
        long eventos;
        long creados;
        long pagados;
        long cancelados;
        long unidades;
        BigDecimal montoPagado = BigDecimal.ZERO;
        final Map<String, Long> porMetodoPago = new HashMap<>();
        final Map<Long, ProductoAcumulado> productos = new HashMap<>();
    }

    private static final class ProductoAcumulado {
        final Long productoId;
        final String nombre;
        long unidades;
        BigDecimal monto = BigDecimal.ZERO;

        ProductoAcumulado(Long productoId, String nombre) {
            this.productoId = productoId;
            this.nombre = nombre;
        }
    }
}
