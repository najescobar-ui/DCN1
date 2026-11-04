package cl.duoc.pedidos360.notificaciones.consumer.notificaciones;

import java.io.IOException;

import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import com.rabbitmq.client.Channel;

import cl.duoc.pedidos360.notificaciones.mensajeria.ConfirmadorMensajes;
import cl.duoc.pedidos360.notificaciones.notificacion.NotificacionService;

/** Dominio notificaciones: avisa al cliente cada cambio de su pedido o de su pago. */
@Component
public class NotificacionConsumer {

    private final ConfirmadorMensajes confirmador;
    private final NotificacionService service;

    public NotificacionConsumer(ConfirmadorMensajes confirmador, NotificacionService service) {
        this.confirmador = confirmador;
        this.service = service;
    }

    @RabbitListener(id = "notificaciones", queues = "${mensajeria.colas.notificaciones.nombre}")
    public void recibir(Message mensaje, Channel canal) throws IOException {
        confirmador.procesar("notificaciones", mensaje, canal, service::registrar);
    }
}
