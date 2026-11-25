package cl.duoc.pedidos360.analitica.consumer.analitica;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import cl.duoc.pedidos360.analitica.config.StreamingProperties;
import cl.duoc.pedidos360.analitica.evento.MetricaVentas;

/**
 * Productor del evento derivado: publica la métrica de una ventana en ventas.metricas con key = id de la ventana.
 * El envío es asíncrono; si falla queda en el log, y la próxima actualización de esa ventana vuelve a publicar la
 * métrica completa (cada mensaje trae el total de la ventana, no un incremento).
 */
@Component
public class PublicadorMetricas {

    private static final Logger log = LoggerFactory.getLogger(PublicadorMetricas.class);

    private final KafkaTemplate<String, MetricaVentas> template;
    private final String topico;

    public PublicadorMetricas(KafkaTemplate<String, MetricaVentas> template, StreamingProperties props) {
        this.template = template;
        this.topico = props.topicos().metricas().nombre();
    }

    public void publicar(MetricaVentas metrica) {
        template.send(topico, metrica.ventana(), metrica).whenComplete((resultado, ex) -> {
            if (ex != null) {
                log.error("No se pudo publicar la métrica {} en {}: {}", metrica.ventana(), topico, ex.getMessage());
            } else {
                log.debug("Métrica {} -> {}-{}@{}", metrica.ventana(), topico,
                        resultado.getRecordMetadata().partition(), resultado.getRecordMetadata().offset());
            }
        });
    }
}
