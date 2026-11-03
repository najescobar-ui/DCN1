package cl.duoc.pedidos360.notificaciones.auditoria;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cl.duoc.pedidos360.notificaciones.evento.EventoPedido;

/** Bitácora de todos los eventos de pedidos y pagos. */
@Service
@Transactional(readOnly = true)
public class AuditoriaService {

    private final RegistroAuditoriaRepository repository;

    public AuditoriaService(RegistroAuditoriaRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public void registrar(EventoPedido evento, String routingKey) {
        if (repository.existsByEventoId(evento.eventoId())) {
            return;
        }
        repository.save(new RegistroAuditoria(evento.eventoId(), routingKey, evento.tipo().name(), evento.pedidoId(),
                evento.estado(), evento.estadoPago(), evento.clienteUsername(), evento.ocurridoEn()));
    }

    public List<RegistroAuditoria> listar() {
        return repository.findTop200ByOrderByRecibidoEnDesc();
    }
}
