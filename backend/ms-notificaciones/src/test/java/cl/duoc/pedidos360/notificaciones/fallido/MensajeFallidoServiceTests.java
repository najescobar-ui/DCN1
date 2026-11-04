package cl.duoc.pedidos360.notificaciones.fallido;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.web.server.ResponseStatusException;

import cl.duoc.pedidos360.notificaciones.mensajeria.ConfirmadorMensajes;
import tools.jackson.databind.json.JsonMapper;

/** Registro de un mensaje de DLQ a partir de x-death y su reproceso hacia el exchange de origen. */
class MensajeFallidoServiceTests {

    private final MensajeFallidoRepository repository = mock(MensajeFallidoRepository.class);
    private final RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
    private final MensajeFallidoService service = new MensajeFallidoService(repository, rabbitTemplate,
            JsonMapper.builder().build());

    private static Message mensajeMuerto(String motivo) {
        MessageProperties p = new MessageProperties();
        p.setMessageId("m-1");
        p.setContentType("application/json");
        p.setReceivedRoutingKey("comprobantes.generar.dlq");
        p.setHeader(ConfirmadorMensajes.HEADER_SIMULAR_FALLO, "bd-caida");
        p.setHeader("x-delivery-count", 3);
        p.setHeader("x-first-death-reason", motivo);
        p.setHeader("x-death", List.of(Map.of(
                "queue", "comprobantes.generar",
                "reason", motivo,
                "count", 1L,
                "exchange", "pedidos.comandos",
                "routing-keys", List.of("comprobante.generar"))));
        return new Message("{\"pedidoId\":7}".getBytes(StandardCharsets.UTF_8), p);
    }

    @Test
    void registraOrigenYMotivoDesdeXDeath() {
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        MensajeFallido f = service.registrar("comprobantes.generar.dlq", mensajeMuerto("rejected"));
        assertThat(f.getColaOrigen()).isEqualTo("comprobantes.generar");
        assertThat(f.getMotivo()).isEqualTo("rejected");
        assertThat(f.getExchangeOrigen()).isEqualTo("pedidos.comandos");
        assertThat(f.getRoutingKeyOrigen()).isEqualTo("comprobante.generar");
        assertThat(f.getCuerpo()).isEqualTo("{\"pedidoId\":7}");
        assertThat(f.getHeaders()).contains(ConfirmadorMensajes.HEADER_SIMULAR_FALLO)
                .doesNotContain("x-death").doesNotContain("x-delivery-count");
    }

    @Test
    void reprocesaAlExchangeDeOrigenSinElHeaderDeDemostracion() {
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        MensajeFallido f = service.registrar("comprobantes.generar.dlq", mensajeMuerto("expired"));
        when(repository.findById(5L)).thenReturn(Optional.of(f));

        service.reprocesar(5L);

        ArgumentCaptor<Message> enviado = ArgumentCaptor.forClass(Message.class);
        verify(rabbitTemplate).send(eq("pedidos.comandos"), eq("comprobante.generar"), enviado.capture());
        assertThat(enviado.getValue().getMessageProperties().getHeaders())
                .doesNotContainKey(ConfirmadorMensajes.HEADER_SIMULAR_FALLO);
        assertThat(enviado.getValue().getMessageProperties().getMessageId()).isEqualTo("m-1");
        assertThat(f.getReprocesadoEn()).isNotNull();
    }

    @Test
    void noReprocesaDosVeces() {
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        MensajeFallido f = service.registrar("comprobantes.generar.dlq", mensajeMuerto("rejected"));
        f.marcarReprocesado();
        when(repository.findById(5L)).thenReturn(Optional.of(f));
        assertThatThrownBy(() -> service.reprocesar(5L)).isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("ya fue reprocesado");
    }
}
