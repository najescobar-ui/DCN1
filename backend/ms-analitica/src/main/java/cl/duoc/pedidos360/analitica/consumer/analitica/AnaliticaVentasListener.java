package cl.duoc.pedidos360.analitica.consumer.analitica;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import cl.duoc.pedidos360.analitica.evento.ActividadPedido;
import cl.duoc.pedidos360.analitica.evento.FalloTransitorioException;
import cl.duoc.pedidos360.analitica.evento.ValidadorActividad;
import cl.duoc.pedidos360.analitica.proyeccion.AgregadorVentas;

/**
 * Grupo analitica-ventas sobre pedidos.actividad: un consumidor por partición. Valida el evento, lo suma a su
 * ventana (AgregadorVentas) y publica la métrica actualizada en ventas.metricas. No tiene lógica de negocio.
 *
 * El header x-simular-fallo (lo agrega ms-streaming-admin en la demo) simula que un recurso no responde, para
 * mostrar los reintentos y el paso a la DLT.
 */
@Component
public class AnaliticaVentasListener {

    static final String HEADER_SIMULAR_FALLO = "x-simular-fallo";
    private static final Logger log = LoggerFactory.getLogger(AnaliticaVentasListener.class);

    private final AgregadorVentas agregador;
    private final PublicadorMetricas publicador;

    public AnaliticaVentasListener(AgregadorVentas agregador, PublicadorMetricas publicador) {
        this.agregador = agregador;
        this.publicador = publicador;
    }

    @KafkaListener(id = "${streaming.grupos.analitica}", groupId = "${streaming.grupos.analitica}",
            topics = "${streaming.topicos.actividad.nombre}", concurrency = "${streaming.concurrencia}")
    public void recibir(@Payload ActividadPedido evento,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int particion,
            @Header(KafkaHeaders.OFFSET) long offset,
            @Header(name = HEADER_SIMULAR_FALLO, required = false) String simularFallo) {
        ValidadorActividad.validar(evento);
        if (simularFallo != null) {
            throw new FalloTransitorioException("fallo recuperable simulado (" + simularFallo + ")");
        }
        agregador.registrar(evento).ifPresentOrElse(metrica -> {
            publicador.publicar(metrica);
            log.info("[analitica] {} pedido={} p{}@{} -> ventana {} (creados={}, pagados={}, monto={})", evento.tipo(),
                    evento.pedidoId(), particion, offset, metrica.ventana(), metrica.pedidosCreados(),
                    metrica.pedidosPagados(), metrica.montoPagado());
        }, () -> log.info("[analitica] {} pedido={} p{}@{} ya contado, se omite", evento.tipo(), evento.pedidoId(),
                particion, offset));
    }
}
