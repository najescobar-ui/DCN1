package cl.duoc.pedidos360.notificaciones.comprobante;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cl.duoc.pedidos360.notificaciones.evento.EventoPedido;
import cl.duoc.pedidos360.notificaciones.evento.TipoEvento;
import cl.duoc.pedidos360.notificaciones.exception.MensajeInvalidoException;

/** Emite el comprobante de un pedido pagado. */
@Service
@Transactional(readOnly = true)
public class ComprobanteService {

    private static final Logger log = LoggerFactory.getLogger(ComprobanteService.class);

    private final ComprobanteRepository repository;

    public ComprobanteService(ComprobanteRepository repository) {
        this.repository = repository;
    }

    /** Idempotente por pedido. Un comando que no sea de pago aprobado es un error del productor (no se reintenta). */
    @Transactional
    public void emitir(EventoPedido evento) {
        if (evento.tipo() != TipoEvento.PAGO_APROBADO || evento.total() == null) {
            throw new MensajeInvalidoException("el comprobante requiere un evento PAGO_APROBADO con total");
        }
        if (repository.existsByPedidoId(evento.pedidoId())) {
            log.info("El pedido {} ya tiene comprobante, se omite", evento.pedidoId());
            return;
        }
        String folio = "C-%06d".formatted(evento.pedidoId());
        repository.save(new Comprobante(folio, evento.pedidoId(), evento.eventoId(), evento.clienteId(),
                evento.clienteUsername(), evento.metodoPago(), evento.total()));
        log.info("Comprobante {} emitido para el pedido {} ({} {})", folio, evento.numeroPedido(), evento.metodoPago(),
                evento.total());
    }

    public List<Comprobante> listar(String clienteId, boolean esAdmin) {
        return esAdmin ? repository.findTop100ByOrderByEmitidoEnDesc()
                : repository.findTop100ByClienteIdOrderByEmitidoEnDesc(clienteId);
    }
}
