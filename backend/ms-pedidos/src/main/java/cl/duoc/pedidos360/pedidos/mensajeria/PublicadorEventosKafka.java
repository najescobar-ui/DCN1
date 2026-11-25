package cl.duoc.pedidos360.pedidos.mensajeria;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.kafka.KafkaException;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import cl.duoc.pedidos360.pedidos.evento.ActividadPedido;

/**
 * Productor Kafka: publica cada hecho de negocio en pedidos.actividad con key = pedidoId (orden por pedido),
 * serializado como JSON y con acks=all (lo confirman al menos 2 de las 3 réplicas, por min.insync.replicas).
 *
 * Igual que con RabbitMQ, se ejecuta después del commit y nunca frena al cliente: el envío es asíncrono, y si
 * Kafka no está disponible el error queda en el log y el pedido sigue su curso. RabbitMQ no se toca: los dos
 * publicadores escuchan eventos de Spring distintos.
 */
@Component
@EnableConfigurationProperties(StreamingProperties.class)
@ConditionalOnProperty(name = "streaming.habilitado", havingValue = "true", matchIfMissing = true)
public class PublicadorEventosKafka {

    private static final Logger log = LoggerFactory.getLogger(PublicadorEventosKafka.class);

    private final KafkaTemplate<String, ActividadPedido> template;
    private final StreamingProperties props;

    public PublicadorEventosKafka(KafkaTemplate<String, ActividadPedido> template, StreamingProperties props) {
        this.template = template;
        this.props = props;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void publicar(ActividadPedido evento) {
        String key = evento.pedidoId().toString();
        try {
            template.send(props.topico(), key, evento).whenComplete((resultado, ex) -> {
                if (ex != null) {
                    log.error("No se pudo publicar {} del pedido {} en {}: {}", evento.tipo(), evento.pedidoId(),
                            props.topico(), ex.getMessage());
                } else {
                    log.info("Publicado {} pedido={} -> {}-{}@{}", evento.tipo(), evento.pedidoId(), props.topico(),
                            resultado.getRecordMetadata().partition(), resultado.getRecordMetadata().offset());
                }
            });
        } catch (KafkaException ex) {
            // Sin metadata del clúster (brokers caídos) el envío falla antes de encolarse, tras max.block.ms.
            log.error("Kafka no disponible para {} del pedido {}: {}", evento.tipo(), evento.pedidoId(), ex.getMessage());
        }
    }
}
