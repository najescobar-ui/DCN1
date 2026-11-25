package cl.duoc.pedidos360.analitica.evento;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Evento derivado del tópico ventas.metricas (key = ventana). Lo publica el grupo analitica-ventas cada vez que
 * cambia una ventana; como el tópico es compactado, de cada ventana queda la última versión.
 *
 * @param ventana           id de la ventana (inicio en UTC, por ejemplo 2026-11-25T15:04Z)
 * @param inicio            inicio de la ventana (incluido)
 * @param fin               fin de la ventana (excluido)
 * @param pedidosCreados    pedidos nuevos en la ventana
 * @param pedidosPagados    pagos aprobados en la ventana
 * @param pedidosCancelados pedidos cancelados en la ventana
 * @param montoPagado       suma de los pagos aprobados
 * @param ticketPromedio    montoPagado / pedidosPagados
 * @param unidadesVendidas  unidades pedidas en la ventana
 * @param topProductos      los cinco productos más pedidos en la ventana
 * @param porMetodoPago     pedidos creados por medio de pago
 * @param eventosProcesados eventos que aportaron a la ventana
 * @param generadoEn        cuándo se calculó esta versión
 */
public record MetricaVentas(
        String ventana,
        Instant inicio,
        Instant fin,
        long pedidosCreados,
        long pedidosPagados,
        long pedidosCancelados,
        BigDecimal montoPagado,
        BigDecimal ticketPromedio,
        long unidadesVendidas,
        List<ProductoVendido> topProductos,
        Map<String, Long> porMetodoPago,
        long eventosProcesados,
        Instant generadoEn) {

    public record ProductoVendido(Long productoId, String nombre, long unidades, BigDecimal monto) {
    }
}
