package cl.duoc.pedidos360.notificaciones.consumer.comprobantes;

import java.io.IOException;

import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import com.rabbitmq.client.Channel;

import cl.duoc.pedidos360.notificaciones.comprobante.ComprobanteService;
import cl.duoc.pedidos360.notificaciones.mensajeria.ConfirmadorMensajes;
import cl.duoc.pedidos360.notificaciones.mensajeria.RegistradorDlq;

/** Dominio comprobantes: atiende el comando comprobante.generar del exchange direct. */
@Component
public class ComprobanteConsumer {

    private final ConfirmadorMensajes confirmador;
    private final ComprobanteService service;
    private final RegistradorDlq registradorDlq;

    public ComprobanteConsumer(ConfirmadorMensajes confirmador, ComprobanteService service, RegistradorDlq registradorDlq) {
        this.confirmador = confirmador;
        this.service = service;
        this.registradorDlq = registradorDlq;
    }

    @RabbitListener(id = "comprobantes", queues = "${mensajeria.colas.comprobantes.nombre}")
    public void recibir(Message mensaje, Channel canal) throws IOException {
        confirmador.procesar("comprobantes", mensaje, canal, service::emitir);
    }

    /** Listener de la DLQ de este dominio: registra en log y en mensaje_fallido cada mensaje no entregado. */
    @RabbitListener(id = "comprobantes-dlq", queues = "${mensajeria.colas.comprobantes.nombre}${mensajeria.sufijo-dlq}")
    public void recibirDlq(Message mensaje, Channel canal) throws IOException {
        registradorDlq.registrar("comprobantes", mensaje, canal);
    }
}
