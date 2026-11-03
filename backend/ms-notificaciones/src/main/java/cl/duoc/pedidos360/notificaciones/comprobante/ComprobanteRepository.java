package cl.duoc.pedidos360.notificaciones.comprobante;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ComprobanteRepository extends JpaRepository<Comprobante, Long> {

    boolean existsByPedidoId(Long pedidoId);

    List<Comprobante> findTop100ByOrderByEmitidoEnDesc();

    List<Comprobante> findTop100ByClienteIdOrderByEmitidoEnDesc(String clienteId);
}
