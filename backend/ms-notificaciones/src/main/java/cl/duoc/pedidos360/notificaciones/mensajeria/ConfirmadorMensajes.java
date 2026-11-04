package cl.duoc.pedidos360.notificaciones.mensajeria;

import java.io.IOException;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.stereotype.Component;

import com.rabbitmq.client.Channel;

import cl.duoc.pedidos360.notificaciones.evento.EventoPedido;
import cl.duoc.pedidos360.notificaciones.exception.FalloSimuladoException;
import cl.duoc.pedidos360.notificaciones.exception.MensajeInvalidoException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * Política de confirmación común a todos los consumidores (los listeners usan AcknowledgeMode.MANUAL).
 *
 * <ul>
 * <li><b>Procesado bien</b> → basicAck: RabbitMQ borra el mensaje.</li>
 * <li><b>Mensaje inválido</b> (JSON ilegible o sin datos obligatorios) → basicNack sin reencolar: va directo
 * a la DLQ, porque reintentarlo daría el mismo error.</li>
 * <li><b>Error recuperable</b> (base de datos caída, fallo puntual) → basicNack reencolando, hasta
 * {@code max-intentos}. El intento se lee del header x-delivery-count que agregan las colas quorum.</li>
 * <li><b>Reintentos agotados</b> → basicNack sin reencolar: va a la DLQ.</li>
 * </ul>
 * Cada decisión queda en el log con la cola, el intento y el motivo.
 */
@Component
public class ConfirmadorMensajes {

    private static final Logger log = LoggerFactory.getLogger(ConfirmadorMensajes.class);
    public static final String HEADER_SIMULAR_FALLO = "x-simular-fallo";

    private final JsonMapper jsonMapper;
    private final MensajeriaProperties props;

    public ConfirmadorMensajes(JsonMapper jsonMapper, MensajeriaProperties props) {
        this.jsonMapper = jsonMapper;
        this.props = props;
    }

    /**
     * Convierte el mensaje, ejecuta la lógica del consumidor y confirma o rechaza según el resultado.
     *
     * @param consumidor nombre para los logs
     * @param logica     trabajo del consumidor; lanza excepción si no pudo completarlo
     */
    public void procesar(String consumidor, Message mensaje, Channel canal, Consumer<EventoPedido> logica)
            throws IOException {
        MessageProperties propiedades = mensaje.getMessageProperties();
        long tag = propiedades.getDeliveryTag();
        int intento = intento(propiedades);
        String cola = propiedades.getConsumerQueue();
        try {
            EventoPedido evento = leer(mensaje);
            simularFalloSiSePide(propiedades);
            logica.accept(evento);
            canal.basicAck(tag, false);
            log.info("[{}] ACK {} pedido={} cola={} intento={}", consumidor, evento.tipo(), evento.pedidoId(), cola,
                    intento);
        } catch (MensajeInvalidoException ex) {
            canal.basicNack(tag, false, false);
            log.error("[{}] NACK a DLQ (mensaje inválido, no se reintenta) cola={} messageId={}: {}", consumidor, cola,
                    propiedades.getMessageId(), ex.getMessage());
        } catch (RuntimeException ex) {
            if (intento < props.maxIntentos()) {
                canal.basicNack(tag, false, true);
                log.warn("[{}] NACK con reencolado, intento {}/{} cola={} messageId={}: {}", consumidor, intento,
                        props.maxIntentos(), cola, propiedades.getMessageId(), ex.getMessage());
            } else {
                canal.basicNack(tag, false, false);
                log.error("[{}] NACK a DLQ (agotó {} intentos) cola={} messageId={}: {}", consumidor,
                        props.maxIntentos(), cola, propiedades.getMessageId(), ex.getMessage());
            }
        }
    }

    private EventoPedido leer(Message mensaje) {
        EventoPedido evento;
        try {
            evento = jsonMapper.readValue(mensaje.getBody(), EventoPedido.class);
        } catch (JacksonException ex) {
            throw new MensajeInvalidoException("JSON ilegible: " + ex.getOriginalMessage(), ex);
        }
        if (evento == null || evento.eventoId() == null || evento.tipo() == null || evento.pedidoId() == null) {
            throw new MensajeInvalidoException("faltan eventoId, tipo o pedidoId");
        }
        return evento;
    }

    /** Número de intento (1 = primera entrega). Las colas quorum cuentan las entregas fallidas en x-delivery-count. */
    static int intento(MessageProperties propiedades) {
        Object conteo = propiedades.getHeader("x-delivery-count");
        if (conteo instanceof Number n) {
            return n.intValue() + 1;
        }
        return Boolean.TRUE.equals(propiedades.getRedelivered()) ? 2 : 1;
    }

    private void simularFalloSiSePide(MessageProperties propiedades) {
        if (props.simularFallos() && propiedades.getHeader(HEADER_SIMULAR_FALLO) != null) {
            throw new FalloSimuladoException("fallo recuperable simulado (" + propiedades.getHeader(HEADER_SIMULAR_FALLO) + ")");
        }
    }
}
