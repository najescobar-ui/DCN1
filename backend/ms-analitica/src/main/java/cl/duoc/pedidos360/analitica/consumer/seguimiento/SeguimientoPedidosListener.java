package cl.duoc.pedidos360.analitica.consumer.seguimiento;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import cl.duoc.pedidos360.analitica.evento.ActividadPedido;
import cl.duoc.pedidos360.analitica.evento.ValidadorActividad;
import cl.duoc.pedidos360.analitica.proyeccion.SeguimientoPedidos;

/**
 * Grupo seguimiento-pedidos sobre pedidos.actividad. Lee los mismos eventos que analitica-ventas, pero con su
 * propio offset: si uno se atrasa o se detiene, el otro no se entera.
 */
@Component
public class SeguimientoPedidosListener {

    private static final Logger log = LoggerFactory.getLogger(SeguimientoPedidosListener.class);

    private final SeguimientoPedidos seguimiento;

    public SeguimientoPedidosListener(SeguimientoPedidos seguimiento) {
        this.seguimiento = seguimiento;
    }

    @KafkaListener(id = "${streaming.grupos.seguimiento}", groupId = "${streaming.grupos.seguimiento}",
            topics = "${streaming.topicos.actividad.nombre}", concurrency = "${streaming.concurrencia}")
    public void recibir(@Payload ActividadPedido evento,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int particion,
            @Header(KafkaHeaders.OFFSET) long offset) {
        ValidadorActividad.validar(evento);
        if (seguimiento.registrar(evento, particion, offset)) {
            log.info("[seguimiento] {} pedido={} estado={} p{}@{}", evento.tipo(), evento.pedidoId(), evento.estado(),
                    particion, offset);
        }
    }
}
