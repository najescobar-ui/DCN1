package cl.duoc.pedidos360.pedidos.mensajeria;

import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import cl.duoc.pedidos360.pedidos.evento.EventoPedido;

/**
 * Productor: traduce los eventos de negocio (EventoPedido) a mensajes RabbitMQ.
 *
 * Se ejecuta después del commit, así nunca se anuncia un pedido que terminó en rollback. Si RabbitMQ no
 * está disponible el error queda en el log y la operación del cliente sigue su curso: la mensajería es
 * asíncrona y no puede tumbar la lógica existente.
 */
@Component
public class PublicadorEventosRabbit {

    private static final Logger log = LoggerFactory.getLogger(PublicadorEventosRabbit.class);

    private final RabbitTemplate rabbitTemplate;
    private final MensajeriaProperties props;

    public PublicadorEventosRabbit(RabbitTemplate rabbitTemplate, MensajeriaProperties props) {
        this.rabbitTemplate = rabbitTemplate;
        this.props = props;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void publicar(EventoPedido evento) {
        MensajeriaProperties.RoutingKeys rk = props.routingKeys();
        switch (evento.tipo()) {
            case PEDIDO_CREADO -> enviar(props.exchanges().eventos(), rk.pedidoCreado(), evento);
            case ESTADO_CAMBIADO -> enviar(props.exchanges().eventos(),
                    rk.pedidoEstado() + "." + evento.estado().name().toLowerCase(Locale.ROOT), evento);
            case PAGO_APROBADO -> {
                enviar(props.exchanges().eventos(), rk.pagoAprobado(), evento);
                // Además del evento, se encarga el comprobante por el exchange direct.
                enviar(props.exchanges().comandos(), rk.generarComprobante(), evento);
            }
            case PAGO_RECHAZADO -> enviar(props.exchanges().eventos(), rk.pagoRechazado(), evento);
        }
    }

    private void enviar(String exchange, String routingKey, EventoPedido evento) {
        try {
            rabbitTemplate.convertAndSend(exchange, routingKey, evento, mensaje -> {
                mensaje.getMessageProperties().setMessageId(evento.eventoId().toString());
                mensaje.getMessageProperties().setType(evento.tipo().name());
                return mensaje;
            }, new CorrelationData(evento.eventoId().toString()));
            log.info("Publicado {} pedido={} -> {} [{}]", evento.tipo(), evento.pedidoId(), exchange, routingKey);
        } catch (AmqpException ex) {
            log.error("No se pudo publicar {} del pedido {} en {} [{}]: {}", evento.tipo(), evento.pedidoId(),
                    exchange, routingKey, ex.getMessage());
        }
    }
}
