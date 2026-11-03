package cl.duoc.pedidos360.notificaciones.auditoria;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface RegistroAuditoriaRepository extends JpaRepository<RegistroAuditoria, Long> {

    boolean existsByEventoId(UUID eventoId);

    List<RegistroAuditoria> findTop200ByOrderByRecibidoEnDesc();
}
