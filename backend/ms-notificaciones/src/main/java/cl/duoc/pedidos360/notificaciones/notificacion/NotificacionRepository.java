package cl.duoc.pedidos360.notificaciones.notificacion;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificacionRepository extends JpaRepository<Notificacion, Long> {

    boolean existsByEventoId(UUID eventoId);

    List<Notificacion> findTop100ByOrderByCreadaEnDesc();

    List<Notificacion> findTop100ByClienteIdOrderByCreadaEnDesc(String clienteId);
}
