package cl.duoc.pedidos360.pedidos.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import cl.duoc.pedidos360.pedidos.model.EstadoPedido;
import cl.duoc.pedidos360.pedidos.model.Pedido;

public interface PedidoRepository extends JpaRepository<Pedido, Long> {

    @EntityGraph(attributePaths = "items")
    List<Pedido> findAllByOrderByCreadoEnDesc();

    @EntityGraph(attributePaths = "items")
    List<Pedido> findByClienteIdOrderByCreadoEnDesc(String clienteId);

    Optional<Pedido> findByPagoToken(String pagoToken);

    Optional<Pedido> findFirstByClienteIdAndEstadoInOrderByCreadoEnDesc(String clienteId, Collection<EstadoPedido> estados);
}
