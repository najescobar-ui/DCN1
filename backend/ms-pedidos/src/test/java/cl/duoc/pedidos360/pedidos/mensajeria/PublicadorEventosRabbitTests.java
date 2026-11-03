package cl.duoc.pedidos360.pedidos.mensajeria;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpConnectException;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import cl.duoc.pedidos360.pedidos.evento.EventoPedido;
import cl.duoc.pedidos360.pedidos.evento.TipoEvento;
import cl.duoc.pedidos360.pedidos.model.EstadoPago;
import cl.duoc.pedidos360.pedidos.model.EstadoPedido;
import cl.duoc.pedidos360.pedidos.model.MetodoPago;

/** Verifica a qué exchange y routing key va cada tipo de evento, y que un broker caído no propague el error. */
class PublicadorEventosRabbitTests {

    private final RabbitTemplate template = mock(RabbitTemplate.class);
    private final MensajeriaProperties props = new MensajeriaProperties(
            new MensajeriaProperties.Exchanges("pedidos.eventos", "pedidos.comandos"),
            new MensajeriaProperties.RoutingKeys("pedido.creado", "pedido.estado", "pago.aprobado", "pago.rechazado",
                    "comprobante.generar"));
    private final PublicadorEventosRabbit publicador = new PublicadorEventosRabbit(template, props);

    private static EventoPedido evento(TipoEvento tipo, EstadoPedido estado) {
        return new EventoPedido(UUID.randomUUID(), tipo, 7L, "sub-1", "cliente1", estado, EstadoPago.PENDIENTE,
                MetodoPago.TARJETA, new BigDecimal("12990"), Instant.now());
    }

    @Test
    void pedidoCreadoVaAlTopicDeEventos() {
        EventoPedido e = evento(TipoEvento.PEDIDO_CREADO, EstadoPedido.PENDIENTE);
        publicador.publicar(e);
        verify(template).convertAndSend(eq("pedidos.eventos"), eq("pedido.creado"), eq(e),
                any(MessagePostProcessor.class), any(CorrelationData.class));
        verifyNoMoreInteractions(template);
    }

    @Test
    void cambioDeEstadoUsaElEstadoEnLaRoutingKey() {
        EventoPedido e = evento(TipoEvento.ESTADO_CAMBIADO, EstadoPedido.DESPACHADO);
        publicador.publicar(e);
        verify(template).convertAndSend(eq("pedidos.eventos"), eq("pedido.estado.despachado"), eq(e),
                any(MessagePostProcessor.class), any(CorrelationData.class));
    }

    @Test
    void pagoAprobadoPublicaEventoYComandoDeComprobante() {
        EventoPedido e = evento(TipoEvento.PAGO_APROBADO, EstadoPedido.CONFIRMADO);
        publicador.publicar(e);
        verify(template).convertAndSend(eq("pedidos.eventos"), eq("pago.aprobado"), eq(e),
                any(MessagePostProcessor.class), any(CorrelationData.class));
        verify(template).convertAndSend(eq("pedidos.comandos"), eq("comprobante.generar"), eq(e),
                any(MessagePostProcessor.class), any(CorrelationData.class));
    }

    @Test
    void brokerCaidoNoInterrumpeElFlujo() {
        doThrow(new AmqpConnectException(new java.net.ConnectException("refused"))).when(template)
                .convertAndSend(any(String.class), any(String.class), any(Object.class),
                        any(MessagePostProcessor.class), any(CorrelationData.class));
        publicador.publicar(evento(TipoEvento.PEDIDO_CREADO, EstadoPedido.PENDIENTE));
    }
}
