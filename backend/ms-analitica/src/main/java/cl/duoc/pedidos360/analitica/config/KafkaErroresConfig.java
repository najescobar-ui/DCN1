package cl.duoc.pedidos360.analitica.config;

import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

import cl.duoc.pedidos360.analitica.evento.EventoInvalidoException;

/**
 * Manejo de errores de los consumidores, común a todos los grupos (Spring Boot lo conecta al contenedor):
 *
 * <ul>
 * <li>Error transitorio: reintenta en el mismo consumidor (streaming.reintentos: 3 intentos, 1 s entre cada uno).
 * Mientras tanto la partición espera, así no se pierde el orden.</li>
 * <li>Evento inválido o ilegible (EventoInvalidoException, DeserializationException): no se reintenta, reintentarlo
 * daría el mismo error.</li>
 * <li>En ambos casos, al final el evento se publica en pedidos.actividad.DLT (misma partición), con headers que
 * dicen el tópico, la partición, el offset, el grupo y la excepción de origen. Luego se confirma el offset y el
 * consumidor sigue: un evento malo nunca bloquea la partición.</li>
 * </ul>
 */
@Configuration
public class KafkaErroresConfig {

    private static final Logger log = LoggerFactory.getLogger(KafkaErroresConfig.class);

    @Bean
    CommonErrorHandler errorHandler(ProducerFactory<Object, Object> producerFactory, StreamingProperties props) {
        DefaultErrorHandler handler = new DefaultErrorHandler(recuperador(producerFactory, props),
                new FixedBackOff(props.reintentos().espera().toMillis(), props.reintentos().intentos() - 1L));
        handler.addNotRetryableExceptions(EventoInvalidoException.class);
        handler.setRetryListeners((registro, ex, intento) -> log.warn("Intento {}/{} falló en {}-{}@{}: {}", intento,
                props.reintentos().intentos(), registro.topic(), registro.partition(), registro.offset(),
                causa(ex).getMessage()));
        return handler;
    }

    /**
     * Publica en la DLT. Un evento ilegible llega como bytes (no se pudo convertir a la clase), así que se reenvía
     * con un serializador de bytes; el resto, con el de JSON.
     */
    private static DeadLetterPublishingRecoverer recuperador(ProducerFactory<Object, Object> producerFactory,
            StreamingProperties props) {
        KafkaTemplate<String, byte[]> bytes = new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(
                producerFactory.getConfigurationProperties(), new StringSerializer(), new ByteArraySerializer()));
        Map<Class<?>, KafkaOperations<?, ?>> plantillas = new LinkedHashMap<>();
        plantillas.put(byte[].class, bytes);
        plantillas.put(Object.class, new KafkaTemplate<>(producerFactory));
        String dlt = props.topicos().errores().nombre();
        return new DeadLetterPublishingRecoverer(plantillas, (registro, ex) -> {
            log.error("A {} {}-{}@{} key={}: {}", dlt, registro.topic(), registro.partition(), registro.offset(),
                    registro.key(), causa(ex).getMessage());
            return new TopicPartition(dlt, registro.partition());
        });
    }

    private static Throwable causa(Throwable ex) {
        Throwable c = ex;
        while (c.getCause() != null && c.getCause() != c) {
            c = c.getCause();
        }
        return c;
    }
}
