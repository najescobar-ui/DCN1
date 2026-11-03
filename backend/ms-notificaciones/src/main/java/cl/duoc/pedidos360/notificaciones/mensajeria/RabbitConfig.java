package cl.duoc.pedidos360.notificaciones.mensajeria;

import org.springframework.amqp.core.AcknowledgeMode;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.ExchangeBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.boot.amqp.autoconfigure.SimpleRabbitListenerContainerFactoryConfigurer;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Topología RabbitMQ de ms-notificaciones. Aquí se declara todo lo que el microservicio necesita en el
 * broker; RabbitAdmin lo crea al conectarse (si ya existe con los mismos parámetros, no hace nada).
 *
 * Rutas de mensajería:
 * <pre>
 *  pedidos.eventos  (topic)  --pedido.# / pago.#------> notificaciones.pedidos --rechazo--> pedidos.dlx --> notificaciones.pedidos.dlq
 *  pedidos.comandos (direct) --comprobante.generar----> comprobantes.generar   --rechazo--> pedidos.dlx --> comprobantes.generar.dlq
 *  pedidos.eventos  (topic)  --#----------------------> auditoria.eventos      --rechazo--> pedidos.dlx --> auditoria.eventos.dlq
 * </pre>
 * Las colas son quorum (replicadas en los dos nodos del clúster). Cada una tiene su DLQ: un mensaje llega
 * ahí cuando el consumidor lo rechaza sin reencolar (basicNack con requeue=false).
 */
@Configuration
@EnableConfigurationProperties(MensajeriaProperties.class)
public class RabbitConfig {

    private final MensajeriaProperties props;

    public RabbitConfig(MensajeriaProperties props) {
        this.props = props;
    }

    // ---------- Exchanges ----------

    /** Hechos de negocio publicados por ms-pedidos (pedido.creado, pedido.estado.*, pago.*). */
    @Bean
    TopicExchange eventosExchange() {
        return ExchangeBuilder.topicExchange(props.exchanges().eventos()).durable(true).build();
    }

    /** Trabajos dirigidos a un solo consumidor (comprobante.generar). */
    @Bean
    DirectExchange comandosExchange() {
        return ExchangeBuilder.directExchange(props.exchanges().comandos()).durable(true).build();
    }

    /** Dead letter exchange compartido: enruta cada mensaje rechazado a la DLQ de su cola de origen. */
    @Bean
    DirectExchange deadLetterExchange() {
        return ExchangeBuilder.directExchange(props.exchanges().dlx()).durable(true).build();
    }

    // ---------- Caso de uso 1: notificar al cliente cada cambio de su pedido o pago ----------

    @Bean
    Queue colaNotificaciones() {
        return colaConDlq(props.colas().notificaciones());
    }

    @Bean
    Queue dlqNotificaciones() {
        return QueueBuilder.durable(props.dlqDe(props.colas().notificaciones())).quorum().build();
    }

    /** Una cola puede tener varios bindings: aquí uno por cada patrón (pedido.# y pago.#). */
    @Bean
    Declarables bindingsNotificaciones(Queue colaNotificaciones, TopicExchange eventosExchange) {
        return new Declarables(props.colas().notificaciones().routingKeys().stream()
                .map(patron -> BindingBuilder.bind(colaNotificaciones).to(eventosExchange).with(patron))
                .toList());
    }

    @Bean
    Binding bindingDlqNotificaciones(Queue dlqNotificaciones, DirectExchange deadLetterExchange) {
        return BindingBuilder.bind(dlqNotificaciones).to(deadLetterExchange).with(dlqNotificaciones.getName());
    }

    // ---------- Caso de uso 2: emitir el comprobante de un pedido pagado ----------

    @Bean
    Queue colaComprobantes() {
        return colaConDlq(props.colas().comprobantes());
    }

    @Bean
    Queue dlqComprobantes() {
        return QueueBuilder.durable(props.dlqDe(props.colas().comprobantes())).quorum().build();
    }

    @Bean
    Binding bindingComprobantes(Queue colaComprobantes, DirectExchange comandosExchange) {
        return BindingBuilder.bind(colaComprobantes).to(comandosExchange)
                .with(props.colas().comprobantes().routingKeys().get(0));
    }

    @Bean
    Binding bindingDlqComprobantes(Queue dlqComprobantes, DirectExchange deadLetterExchange) {
        return BindingBuilder.bind(dlqComprobantes).to(deadLetterExchange).with(dlqComprobantes.getName());
    }

    // ---------- Caso de uso 3: bitácora de auditoría de todos los eventos ----------

    @Bean
    Queue colaAuditoria() {
        return colaConDlq(props.colas().auditoria());
    }

    @Bean
    Queue dlqAuditoria() {
        return QueueBuilder.durable(props.dlqDe(props.colas().auditoria())).quorum().build();
    }

    @Bean
    Binding bindingAuditoria(Queue colaAuditoria, TopicExchange eventosExchange) {
        return BindingBuilder.bind(colaAuditoria).to(eventosExchange).with(props.colas().auditoria().routingKeys().get(0));
    }

    @Bean
    Binding bindingDlqAuditoria(Queue dlqAuditoria, DirectExchange deadLetterExchange) {
        return BindingBuilder.bind(dlqAuditoria).to(deadLetterExchange).with(dlqAuditoria.getName());
    }

    // ---------- Consumidores ----------

    /**
     * Fábrica de los @RabbitListener: confirmación MANUAL (cada consumidor decide ack o nack), prefetch
     * limitado y sin reencolar por defecto, para que una excepción no controlada no genere un bucle.
     */
    @Bean
    SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            SimpleRabbitListenerContainerFactoryConfigurer configurer, ConnectionFactory connectionFactory) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        configurer.configure(factory, connectionFactory);
        factory.setAcknowledgeMode(AcknowledgeMode.MANUAL);
        factory.setPrefetchCount(props.prefetch());
        factory.setDefaultRequeueRejected(false);
        return factory;
    }

    /**
     * Cola quorum con su DLX. El delivery-limit del broker es una red de seguridad por encima de los
     * reintentos que controla el consumidor (por si un consumidor muere antes de confirmar).
     */
    private Queue colaConDlq(MensajeriaProperties.Cola cola) {
        return QueueBuilder.durable(cola.nombre())
                .quorum()
                .deadLetterExchange(props.exchanges().dlx())
                .deadLetterRoutingKey(props.dlqDe(cola))
                .deliveryLimit(props.maxIntentos() + 2)
                .build();
    }
}
