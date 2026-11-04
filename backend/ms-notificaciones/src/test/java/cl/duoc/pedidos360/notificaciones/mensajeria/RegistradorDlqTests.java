package cl.duoc.pedidos360.notificaciones.mensajeria;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;

import com.rabbitmq.client.Channel;

import cl.duoc.pedidos360.notificaciones.fallido.MensajeFallido;
import cl.duoc.pedidos360.notificaciones.fallido.MensajeFallidoService;

/** El listener de DLQ confirma lo que registró y reencola si no pudo registrarlo. */
class RegistradorDlqTests {

    private static final long TAG = 7L;
    private final MensajeFallidoService service = mock(MensajeFallidoService.class);
    private final RegistradorDlq registrador = new RegistradorDlq(service);
    private final Channel canal = mock(Channel.class);

    private static Message mensaje() {
        MessageProperties p = new MessageProperties();
        p.setDeliveryTag(TAG);
        p.setConsumerQueue("auditoria.eventos.dlq");
        return new Message("x".getBytes(StandardCharsets.UTF_8), p);
    }

    @Test
    void registraYConfirma() throws Exception {
        when(service.registrar(anyString(), any())).thenReturn(new MensajeFallido("auditoria.eventos.dlq",
                "auditoria.eventos", "rejected", 1, "pedidos.eventos", "pedido.creado", "m", null, "{}", "x"));
        registrador.registrar("auditoria", mensaje(), canal);
        verify(canal).basicAck(TAG, false);
    }

    @Test
    void siNoPuedeRegistrarReencola() throws Exception {
        when(service.registrar(anyString(), any())).thenThrow(new IllegalStateException("bd caída"));
        registrador.registrar("auditoria", mensaje(), canal);
        verify(canal).basicNack(TAG, false, true);
        verify(canal, never()).basicAck(TAG, false);
    }
}
