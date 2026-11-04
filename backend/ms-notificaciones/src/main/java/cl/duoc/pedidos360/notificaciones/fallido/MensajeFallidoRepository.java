package cl.duoc.pedidos360.notificaciones.fallido;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface MensajeFallidoRepository extends JpaRepository<MensajeFallido, Long> {

    List<MensajeFallido> findTop200ByOrderByIdDesc();

    long countByReprocesadoEnIsNull();
}
