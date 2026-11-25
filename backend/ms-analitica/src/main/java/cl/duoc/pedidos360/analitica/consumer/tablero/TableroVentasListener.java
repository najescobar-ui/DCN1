package cl.duoc.pedidos360.analitica.consumer.tablero;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import cl.duoc.pedidos360.analitica.evento.EventoInvalidoException;
import cl.duoc.pedidos360.analitica.evento.MetricaVentas;
import cl.duoc.pedidos360.analitica.proyeccion.TableroVentas;

/**
 * Grupo tablero-ventas sobre ventas.metricas: el tablero del administrador se alimenta del evento derivado, no
 * del tópico de actividad. Este listener deserializa el valor como MetricaVentas (properties de abajo).
 */
@Component
public class TableroVentasListener {

    private static final Logger log = LoggerFactory.getLogger(TableroVentasListener.class);

    private final TableroVentas tablero;

    public TableroVentasListener(TableroVentas tablero) {
        this.tablero = tablero;
    }

    @KafkaListener(id = "${streaming.grupos.tablero}", groupId = "${streaming.grupos.tablero}",
            topics = "${streaming.topicos.metricas.nombre}", concurrency = "${streaming.concurrencia}",
            properties = "spring.json.value.default.type=cl.duoc.pedidos360.analitica.evento.MetricaVentas")
    public void recibir(@Payload MetricaVentas metrica) {
        if (metrica.ventana() == null || metrica.generadoEn() == null) {
            throw new EventoInvalidoException("métrica sin ventana o sin fecha de generación");
        }
        tablero.registrar(metrica);
        log.debug("[tablero] ventana {} actualizada", metrica.ventana());
    }
}
