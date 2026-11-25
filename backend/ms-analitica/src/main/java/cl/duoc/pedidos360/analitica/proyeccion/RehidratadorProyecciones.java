package cl.duoc.pedidos360.analitica.proyeccion;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.TopicPartition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.stereotype.Component;

import cl.duoc.pedidos360.analitica.config.StreamingProperties;
import cl.duoc.pedidos360.analitica.evento.ActividadPedido;
import cl.duoc.pedidos360.analitica.evento.EventoInvalidoException;
import cl.duoc.pedidos360.analitica.evento.MetricaVentas;
import cl.duoc.pedidos360.analitica.evento.ValidadorActividad;

/**
 * Las proyecciones viven en memoria. Al arrancar se reconstruyen releyendo los tópicos desde el inicio de lo
 * retenido: es la ventaja de un log frente a una cola, donde lo consumido ya no existe.
 *
 * La relectura usa un consumidor sin grupo (assign, no subscribe), así no mueve los offsets de ningún consumer
 * group. Cuando termina, arranca los listeners, que retoman desde su offset confirmado; lo que se relea dos veces
 * lo descartan las proyecciones por eventoId.
 */
@Component
public class RehidratadorProyecciones {

    private static final Logger log = LoggerFactory.getLogger(RehidratadorProyecciones.class);
    private static final Duration LIMITE = Duration.ofSeconds(60);

    private final ConsumerFactory<Object, Object> consumerFactory;
    private final KafkaListenerEndpointRegistry registry;
    private final StreamingProperties props;
    private final AgregadorVentas agregador;
    private final SeguimientoPedidos seguimiento;
    private final TableroVentas tablero;

    public RehidratadorProyecciones(ConsumerFactory<Object, Object> consumerFactory,
            KafkaListenerEndpointRegistry registry, StreamingProperties props, AgregadorVentas agregador,
            SeguimientoPedidos seguimiento, TableroVentas tablero) {
        this.consumerFactory = consumerFactory;
        this.registry = registry;
        this.props = props;
        this.agregador = agregador;
        this.seguimiento = seguimiento;
        this.tablero = tablero;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void alArrancar() {
        try {
            int actividad = releer(props.topicos().actividad().nombre(), ActividadPedido.class, r -> {
                ActividadPedido e = (ActividadPedido) r.value();
                ValidadorActividad.validar(e);
                agregador.registrar(e);
                seguimiento.registrar(e, r.partition(), r.offset());
            });
            int metricas = releer(props.topicos().metricas().nombre(), MetricaVentas.class,
                    r -> tablero.registrar((MetricaVentas) r.value()));
            log.info("Proyecciones reconstruidas desde el log: {} eventos de actividad, {} métricas, {} pedidos en seguimiento",
                    actividad, metricas, seguimiento.cantidad());
        } catch (RuntimeException ex) {
            log.warn("No se pudieron reconstruir las proyecciones ({}); los consumidores parten igual", ex.getMessage());
        }
        registry.getListenerContainers().forEach(c -> {
            c.start();
            log.info("Consumidor {} iniciado", c.getListenerId());
        });
    }

    /** Lee el tópico completo hasta el último offset que existía al empezar. */
    private int releer(String topico, Class<?> tipo, Consumer<ConsumerRecord<Object, Object>> aplicar) {
        Map<String, Object> config = Map.of(
                ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false,
                "spring.json.value.default.type", tipo.getName());
        int leidos = 0;
        try (var consumer = consumerFactory.createConsumer(null, "rehidratador-", null, toProperties(config))) {
            List<TopicPartition> particiones = consumer.partitionsFor(topico, LIMITE).stream()
                    .map(p -> new TopicPartition(topico, p.partition())).toList();
            consumer.assign(particiones);
            consumer.seekToBeginning(particiones);
            Map<TopicPartition, Long> fin = consumer.endOffsets(particiones);
            Instant limite = Instant.now().plus(LIMITE);
            while (!alcanzado(consumer, fin) && Instant.now().isBefore(limite)) {
                for (ConsumerRecord<Object, Object> r : consumer.poll(Duration.ofMillis(500))) {
                    if (r.value() == null) {
                        continue; // ilegible: ya quedó en la DLT cuando lo leyó el consumer group
                    }
                    try {
                        aplicar.accept(r);
                        leidos++;
                    } catch (EventoInvalidoException ex) {
                        log.debug("Se omite {}-{}@{} al reconstruir: {}", topico, r.partition(), r.offset(), ex.getMessage());
                    }
                }
            }
        }
        return leidos;
    }

    private static boolean alcanzado(org.apache.kafka.clients.consumer.Consumer<?, ?> consumer,
            Map<TopicPartition, Long> fin) {
        return fin.entrySet().stream().allMatch(e -> consumer.position(e.getKey()) >= e.getValue());
    }

    private static java.util.Properties toProperties(Map<String, Object> mapa) {
        java.util.Properties p = new java.util.Properties();
        p.putAll(mapa);
        return p;
    }
}
