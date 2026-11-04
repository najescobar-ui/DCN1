package cl.duoc.pedidos360.pedidos.mensajeria;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.ExchangeBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuración de mensajería del lado productor. ms-pedidos solo publica, así que declara los
 * exchanges a los que envía; las colas y sus bindings los declara quien consume (ms-notificaciones).
 * Declarar un exchange que ya existe con los mismos parámetros no hace nada, así que el orden de arranque no importa.
 */
@Configuration
@EnableConfigurationProperties(MensajeriaProperties.class)
public class RabbitConfig {

    private static final Logger log = LoggerFactory.getLogger(RabbitConfig.class);

    /** Exchange topic de eventos de pedidos y pagos (pedido.creado, pedido.estado.*, pago.*). */
    @Bean
    TopicExchange eventosExchange(MensajeriaProperties props) {
        return ExchangeBuilder.topicExchange(props.exchanges().eventos()).durable(true).build();
    }

    /** Exchange direct de comandos (comprobante.generar). */
    @Bean
    DirectExchange comandosExchange(MensajeriaProperties props) {
        return ExchangeBuilder.directExchange(props.exchanges().comandos()).durable(true).build();
    }

    /** Los mensajes viajan como JSON, legibles desde la consola de RabbitMQ. */
    @Bean
    MessageConverter jsonMessageConverter() {
        return new JacksonJsonMessageConverter();
    }

    /**
     * Plantilla con confirmaciones del broker: si RabbitMQ no acepta el mensaje (nack) o no hay ninguna
     * cola que lo reciba (return, por mandatory=true), queda registrado en el log.
     */
    @Bean
    RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory, MessageConverter jsonMessageConverter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(jsonMessageConverter);
        template.setMandatory(true);
        template.setConfirmCallback((correlacion, ack, causa) -> {
            if (!ack) {
                log.error("RabbitMQ rechazó el mensaje {}: {}", correlacion != null ? correlacion.getId() : "?", causa);
            }
        });
        template.setReturnsCallback(devuelto -> log.warn("Mensaje sin cola de destino: exchange={} routingKey={} motivo={}",
                devuelto.getExchange(), devuelto.getRoutingKey(), devuelto.getReplyText()));
        return template;
    }
}
