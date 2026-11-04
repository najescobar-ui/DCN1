package cl.duoc.pedidos360.notificaciones.mensajeria;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.stereotype.Component;

import com.rabbitmq.client.Channel;

import cl.duoc.pedidos360.notificaciones.fallido.MensajeFallido;
import cl.duoc.pedidos360.notificaciones.fallido.MensajeFallidoService;

/**
 * Política común de los listeners de DLQ: registrar el mensaje no entregado (log + tabla mensaje_fallido)
 * y confirmarlo con ACK. Si el registro falla (base de datos caída), se reencola para no perderlo; el
 * delivery-limit de la DLQ corta el bucle si el error no se resuelve.
 */
@Component
public class RegistradorDlq {

    private static final Logger log = LoggerFactory.getLogger(RegistradorDlq.class);

    private final MensajeFallidoService service;

    public RegistradorDlq(MensajeFallidoService service) {
        this.service = service;
    }

    public void registrar(String consumidor, Message mensaje, Channel canal) throws IOException {
        long tag = mensaje.getMessageProperties().getDeliveryTag();
        String dlq = mensaje.getMessageProperties().getConsumerQueue();
        try {
            MensajeFallido f = service.registrar(dlq, mensaje);
            canal.basicAck(tag, false);
            log.warn("[{}-dlq] Mensaje no entregado registrado id={} dlq={} colaOrigen={} motivo={} veces={} "
                    + "exchange={} routingKey={} messageId={} cuerpo={}", consumidor, f.getId(), dlq,
                    f.getColaOrigen(), f.getMotivo(), f.getVeces(), f.getExchangeOrigen(), f.getRoutingKeyOrigen(),
                    f.getMessageId(), resumen(f.getCuerpo()));
        } catch (RuntimeException ex) {
            canal.basicNack(tag, false, true);
            log.error("[{}-dlq] No se pudo registrar el mensaje de {} (se reintenta) messageId={}: {}", consumidor,
                    dlq, mensaje.getMessageProperties().getMessageId(), ex.getMessage());
        }
    }

    private static String resumen(String cuerpo) {
        if (cuerpo == null) {
            return "";
        }
        String una = cuerpo.replaceAll("\\s+", " ");
        return una.length() > 160 ? una.substring(0, 160) + "…" : una;
    }
}
