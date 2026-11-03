package cl.duoc.pedidos360.pedidos.mensajeria;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Nombres de exchanges y routing keys, centralizados en application.yml (bloque "mensajeria").
 * Ninguna clase escribe estos nombres a mano: todas los leen desde aquí.
 */
@ConfigurationProperties("mensajeria")
public record MensajeriaProperties(Exchanges exchanges, RoutingKeys routingKeys) {

    /**
     * @param eventos  exchange topic: difunde hechos de negocio a quien quiera escucharlos
     * @param comandos exchange direct: encarga un trabajo puntual a un único consumidor
     */
    public record Exchanges(String eventos, String comandos) {
    }

    /**
     * @param pedidoCreado       evento de pedido nuevo
     * @param pedidoEstado       prefijo del cambio de estado; se completa con el estado (pedido.estado.confirmado)
     * @param pagoAprobado       evento de pago aprobado
     * @param pagoRechazado      evento de pago rechazado o anulado
     * @param generarComprobante comando para emitir el comprobante de un pedido pagado
     */
    public record RoutingKeys(String pedidoCreado, String pedidoEstado, String pagoAprobado, String pagoRechazado,
            String generarComprobante) {
    }
}
