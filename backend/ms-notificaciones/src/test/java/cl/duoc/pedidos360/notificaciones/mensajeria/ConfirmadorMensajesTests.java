package cl.duoc.pedidos360.notificaciones.mensajeria;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;

import com.rabbitmq.client.Channel;

import cl.duoc.pedidos360.notificaciones.evento.EventoPedido;
import cl.duoc.pedidos360.notificaciones.evento.TipoEvento;
import tools.jackson.databind.json.JsonMapper;

/** Cada camino de la política de confirmación: ack, nack a DLQ, reintento y reintentos agotados. */
class ConfirmadorMensajesTests {

    private static final long TAG = 42L;
    private static final String JSON_OK = """
            {"eventoId":"6f1c2a52-3b0e-4f43-9d55-0a5f1f0d8e11","tipo":"PEDIDO_CREADO","pedidoId":12,
             "clienteId":"sub-1","clienteUsername":"cliente1","estado":"PENDIENTE","total":12990}
            """;

    private final MensajeriaProperties props = new MensajeriaProperties(
            new MensajeriaProperties.Exchanges("pedidos.eventos", "pedidos.comandos", "pedidos.dlx"),
            new MensajeriaProperties.Colas(
                    new MensajeriaProperties.Cola("notificaciones.pedidos", List.of("pedido.#")),
                    new MensajeriaProperties.Cola("comprobantes.generar", List.of("comprobante.generar")),
                    new MensajeriaProperties.Cola("auditoria.eventos", List.of("#"))),
            ".dlq", 3, 10, true,
            new MensajeriaProperties.Retencion(new MensajeriaProperties.Limites(86_400_000, 10_000),
                    new MensajeriaProperties.Limites(604_800_000, 1_000)));
    private final ConfirmadorMensajes confirmador = new ConfirmadorMensajes(JsonMapper.builder().build(), props);
    private final Channel canal = mock(Channel.class);

    private static Message mensaje(String cuerpo, Integer deliveryCount, String simularFallo) {
        MessageProperties p = new MessageProperties();
        p.setDeliveryTag(TAG);
        p.setConsumerQueue("notificaciones.pedidos");
        if (deliveryCount != null) {
            p.setHeader("x-delivery-count", deliveryCount);
        }
        if (simularFallo != null) {
            p.setHeader(ConfirmadorMensajes.HEADER_SIMULAR_FALLO, simularFallo);
        }
        return new Message(cuerpo.getBytes(StandardCharsets.UTF_8), p);
    }

    @Test
    void procesadoBienHaceAck() throws Exception {
        AtomicReference<EventoPedido> recibido = new AtomicReference<>();
        confirmador.procesar("test", mensaje(JSON_OK, null, null), canal, recibido::set);
        verify(canal).basicAck(TAG, false);
        assertThat(recibido.get().tipo()).isEqualTo(TipoEvento.PEDIDO_CREADO);
        assertThat(recibido.get().pedidoId()).isEqualTo(12L);
    }

    @Test
    void jsonIlegibleVaDirectoADlqSinEjecutarLaLogica() throws Exception {
        confirmador.procesar("test", mensaje("esto no es json", null, null), canal,
                e -> { throw new AssertionError("no debe ejecutarse"); });
        verify(canal).basicNack(TAG, false, false);
        verify(canal, never()).basicAck(TAG, false);
    }

    @Test
    void faltanDatosObligatoriosVaADlq() throws Exception {
        confirmador.procesar("test", mensaje("{\"tipo\":\"PEDIDO_CREADO\"}", null, null), canal, e -> { });
        verify(canal).basicNack(TAG, false, false);
    }

    @Test
    void errorRecuperableSeReencolaEnElPrimerIntento() throws Exception {
        confirmador.procesar("test", mensaje(JSON_OK, null, null), canal,
                e -> { throw new IllegalStateException("base de datos no disponible"); });
        verify(canal).basicNack(TAG, false, true);
    }

    @Test
    void errorRecuperableEnElUltimoIntentoVaADlq() throws Exception {
        // x-delivery-count=2 significa que ya hubo dos entregas fallidas: esta es la tercera y última.
        confirmador.procesar("test", mensaje(JSON_OK, 2, null), canal,
                e -> { throw new IllegalStateException("sigue fallando"); });
        verify(canal).basicNack(TAG, false, false);
    }

    @Test
    void falloSimuladoSeTrataComoRecuperable() throws Exception {
        confirmador.procesar("test", mensaje(JSON_OK, 0, "transitorio"), canal, e -> { });
        verify(canal).basicNack(TAG, false, true);
    }

    @Test
    void sinSimulacionHabilitadaElHeaderSeIgnora() throws Exception {
        MensajeriaProperties sinDemo = new MensajeriaProperties(props.exchanges(), props.colas(), ".dlq", 3, 10, false,
                props.retencion());
        new ConfirmadorMensajes(JsonMapper.builder().build(), sinDemo)
                .procesar("test", mensaje(JSON_OK, null, "transitorio"), canal, e -> { });
        verify(canal).basicAck(TAG, false);
    }

    @Test
    void elIntentoSeCalculaDesdeXDeliveryCount() {
        assertThat(ConfirmadorMensajes.intento(mensaje(JSON_OK, null, null).getMessageProperties())).isEqualTo(1);
        assertThat(ConfirmadorMensajes.intento(mensaje(JSON_OK, 1, null).getMessageProperties())).isEqualTo(2);
        verifyNoInteractions(canal);
    }
}
