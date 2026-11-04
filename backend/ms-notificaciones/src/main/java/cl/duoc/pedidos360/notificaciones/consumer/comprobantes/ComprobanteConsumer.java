package cl.duoc.pedidos360.notificaciones.consumer.comprobantes;

import java.io.IOException;

import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import com.rabbitmq.client.Channel;

import cl.duoc.pedidos360.notificaciones.comprobante.ComprobanteService;
import cl.duoc.pedidos360.notificaciones.mensajeria.ConfirmadorMensajes;

/** Dominio comprobantes: atiende el comando comprobante.generar del exchange direct. */
@Component
public class ComprobanteConsumer {

    private final ConfirmadorMensajes confirmador;
    private final ComprobanteService service;

    public ComprobanteConsumer(ConfirmadorMensajes confirmador, ComprobanteService service) {
        this.confirmador = confirmador;
        this.service = service;
    }

    @RabbitListener(id = "comprobantes", queues = "${mensajeria.colas.comprobantes.nombre}")
    public void recibir(Message mensaje, Channel canal) throws IOException {
        confirmador.procesar("comprobantes", mensaje, canal, service::emitir);
    }
}
