package cl.duoc.pedidos360.notificaciones.consumer.auditoria;

import java.io.IOException;

import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import com.rabbitmq.client.Channel;

import cl.duoc.pedidos360.notificaciones.auditoria.AuditoriaService;
import cl.duoc.pedidos360.notificaciones.mensajeria.ConfirmadorMensajes;

/** Dominio auditoría: guarda en la bitácora todo lo que pasa por el exchange de eventos. */
@Component
public class AuditoriaConsumer {

    private final ConfirmadorMensajes confirmador;
    private final AuditoriaService service;

    public AuditoriaConsumer(ConfirmadorMensajes confirmador, AuditoriaService service) {
        this.confirmador = confirmador;
        this.service = service;
    }

    @RabbitListener(id = "auditoria", queues = "${mensajeria.colas.auditoria.nombre}")
    public void recibir(Message mensaje, Channel canal) throws IOException {
        String routingKey = mensaje.getMessageProperties().getReceivedRoutingKey();
        confirmador.procesar("auditoria", mensaje, canal, evento -> service.registrar(evento, routingKey));
    }
}
