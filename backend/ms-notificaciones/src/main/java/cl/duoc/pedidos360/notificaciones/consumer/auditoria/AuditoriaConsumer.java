package cl.duoc.pedidos360.notificaciones.consumer.auditoria;

import java.io.IOException;

import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import com.rabbitmq.client.Channel;

import cl.duoc.pedidos360.notificaciones.auditoria.AuditoriaService;
import cl.duoc.pedidos360.notificaciones.mensajeria.ConfirmadorMensajes;
import cl.duoc.pedidos360.notificaciones.mensajeria.RegistradorDlq;

/** Dominio auditoría: guarda en la bitácora todo lo que pasa por el exchange de eventos. */
@Component
public class AuditoriaConsumer {

    private final ConfirmadorMensajes confirmador;
    private final AuditoriaService service;
    private final RegistradorDlq registradorDlq;

    public AuditoriaConsumer(ConfirmadorMensajes confirmador, AuditoriaService service, RegistradorDlq registradorDlq) {
        this.confirmador = confirmador;
        this.service = service;
        this.registradorDlq = registradorDlq;
    }

    @RabbitListener(id = "auditoria", queues = "${mensajeria.colas.auditoria.nombre}")
    public void recibir(Message mensaje, Channel canal) throws IOException {
        String routingKey = mensaje.getMessageProperties().getReceivedRoutingKey();
        confirmador.procesar("auditoria", mensaje, canal, evento -> service.registrar(evento, routingKey));
    }

    /** Listener de la DLQ de este dominio: registra en log y en mensaje_fallido cada mensaje no entregado. */
    @RabbitListener(id = "auditoria-dlq", queues = "${mensajeria.colas.auditoria.nombre}${mensajeria.sufijo-dlq}")
    public void recibirDlq(Message mensaje, Channel canal) throws IOException {
        registradorDlq.registrar("auditoria", mensaje, canal);
    }
}
