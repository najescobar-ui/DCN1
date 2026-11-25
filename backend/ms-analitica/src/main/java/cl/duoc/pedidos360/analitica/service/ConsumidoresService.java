package cl.duoc.pedidos360.analitica.service;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.apache.kafka.common.TopicPartition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.listener.ConcurrentMessageListenerContainer;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.stereotype.Service;

import cl.duoc.pedidos360.analitica.config.StreamingProperties;
import cl.duoc.pedidos360.analitica.dto.ConsumidorInfo;
import cl.duoc.pedidos360.analitica.exception.RecursoNoEncontradoException;

/**
 * Control de los consumidores de esta instancia (los contenedores de @KafkaListener). Permite ver qué partición
 * tiene cada consumidor y cambiar cuántos hay en un grupo:
 * <ul>
 * <li>3 → uno por partición;</li>
 * <li>1 o 2 → Kafka rebalancea y algún consumidor queda con más de una partición;</li>
 * <li>0 → el grupo queda sin miembros: los eventos se siguen acumulando en el tópico y el lag sube. Al volver a
 * encenderlo, el grupo retoma desde su último offset confirmado y se pone al día sin perder nada.</li>
 * </ul>
 */
@Service
public class ConsumidoresService {

    private static final Logger log = LoggerFactory.getLogger(ConsumidoresService.class);

    private final KafkaListenerEndpointRegistry registry;
    private final int maximo;

    public ConsumidoresService(KafkaListenerEndpointRegistry registry, StreamingProperties props) {
        this.registry = registry;
        this.maximo = props.topicos().actividad().particiones();
    }

    public List<ConsumidorInfo> listar() {
        return registry.getListenerContainers().stream()
                .map(this::info)
                .sorted(Comparator.comparing(ConsumidorInfo::grupo))
                .toList();
    }

    public ConsumidorInfo obtener(String id) {
        return info(contenedor(id));
    }

    /** Cambia la cantidad de consumidores del grupo (0 lo detiene). */
    public ConsumidorInfo cambiarConcurrencia(String id, int concurrencia) {
        MessageListenerContainer c = contenedor(id);
        int nueva = Math.min(concurrencia, maximo);
        if (c.isRunning()) {
            c.stop();
        }
        if (nueva > 0) {
            if (c instanceof ConcurrentMessageListenerContainer<?, ?> concurrente) {
                concurrente.setConcurrency(nueva);
            }
            c.start();
        }
        log.info("Consumidores de {}: {}", id, nueva == 0 ? "detenidos" : nueva);
        return info(c);
    }

    private MessageListenerContainer contenedor(String id) {
        MessageListenerContainer c = registry.getListenerContainer(id);
        if (c == null) {
            throw new RecursoNoEncontradoException("No existe el consumidor " + id);
        }
        return c;
    }

    private ConsumidorInfo info(MessageListenerContainer c) {
        int concurrencia = c instanceof ConcurrentMessageListenerContainer<?, ?> cc ? cc.getConcurrency() : 1;
        Map<String, List<String>> asignaciones = new TreeMap<>();
        Map<String, Collection<TopicPartition>> porCliente = c.getAssignmentsByClientId();
        if (porCliente != null) {
            porCliente.forEach((cliente, particiones) -> asignaciones.put(cliente, particiones.stream()
                    .sorted(Comparator.comparing(TopicPartition::topic).thenComparingInt(TopicPartition::partition))
                    .map(TopicPartition::toString)
                    .toList()));
        }
        String grupo = c.getGroupId() == null ? c.getListenerId() : c.getGroupId();
        String[] topicos = c.getContainerProperties().getTopics();
        return new ConsumidorInfo(c.getListenerId(), grupo, topicos == null ? List.of() : List.of(topicos),
                c.isRunning(), c.isRunning() ? concurrencia : 0, maximo, asignaciones);
    }
}
