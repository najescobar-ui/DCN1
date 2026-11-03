package cl.duoc.pedidos360.notificaciones.notificacion;

import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cl.duoc.pedidos360.notificaciones.evento.EventoPedido;

/** Arma y registra el aviso al cliente. No sabe nada de RabbitMQ: recibe el evento ya leído. */
@Service
@Transactional(readOnly = true)
public class NotificacionService {

    private static final Logger log = LoggerFactory.getLogger(NotificacionService.class);
    private static final NumberFormat PESOS = NumberFormat.getCurrencyInstance(Locale.of("es", "CL"));

    private final NotificacionRepository repository;

    public NotificacionService(NotificacionRepository repository) {
        this.repository = repository;
    }

    /** Idempotente: si el evento ya generó un aviso, no hace nada. */
    @Transactional
    public void registrar(EventoPedido evento) {
        if (repository.existsByEventoId(evento.eventoId())) {
            log.info("Evento {} ya notificado, se omite", evento.eventoId());
            return;
        }
        String texto = mensajePara(evento);
        repository.save(new Notificacion(evento.eventoId(), evento.pedidoId(), evento.clienteId(),
                evento.clienteUsername(), evento.tipo().name(), texto));
        // Punto de integración con un canal real (correo, WhatsApp); por ahora queda registrado y en el log.
        log.info("Notificación para {}: {}", evento.clienteUsername(), texto);
    }

    public List<Notificacion> listar(String clienteId, boolean esAdmin) {
        return esAdmin ? repository.findTop100ByOrderByCreadaEnDesc()
                : repository.findTop100ByClienteIdOrderByCreadaEnDesc(clienteId);
    }

    static String mensajePara(EventoPedido e) {
        String numero = e.numeroPedido();
        String pedido = "Tu pedido " + numero;
        return switch (e.tipo()) {
            case PEDIDO_CREADO -> "Recibimos tu pedido " + numero + " por " + monto(e) + ". Te avisaremos cada avance.";
            case PAGO_APROBADO -> "Pago aprobado: el pedido " + numero + " está pagado (" + monto(e) + ").";
            case PAGO_RECHAZADO -> "El pago del pedido " + numero + " no se completó. Puedes intentarlo de nuevo.";
            case ESTADO_CAMBIADO -> switch (e.estado() == null ? "" : e.estado()) {
                case "CONFIRMADO" -> pedido + " fue confirmado y ya está en preparación.";
                case "DESPACHADO" -> pedido + " va en camino.";
                case "ENTREGADO" -> pedido + " fue entregado. ¡Que lo disfrutes!";
                case "CANCELADO" -> pedido + " fue cancelado.";
                default -> pedido + " cambió a " + e.estado() + ".";
            };
        };
    }

    private static String monto(EventoPedido e) {
        return e.total() == null ? "-" : PESOS.format(e.total());
    }
}
