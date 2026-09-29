package cl.duoc.pedidos360.pedidos.service;

import java.util.List;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cl.duoc.pedidos360.pedidos.client.ProductoDto;
import cl.duoc.pedidos360.pedidos.client.ProductosClient;
import cl.duoc.pedidos360.pedidos.dto.CrearPedidoRequest;
import cl.duoc.pedidos360.pedidos.dto.ItemRequest;
import cl.duoc.pedidos360.pedidos.dto.PedidoResponse;
import cl.duoc.pedidos360.pedidos.exception.OperacionNoPermitidaException;
import cl.duoc.pedidos360.pedidos.exception.ProductoNoDisponibleException;
import cl.duoc.pedidos360.pedidos.exception.RecursoNoEncontradoException;
import cl.duoc.pedidos360.pedidos.model.EstadoPago;
import cl.duoc.pedidos360.pedidos.model.EstadoPedido;
import cl.duoc.pedidos360.pedidos.model.ItemPedido;
import cl.duoc.pedidos360.pedidos.model.MetodoPago;
import cl.duoc.pedidos360.pedidos.model.Pedido;
import cl.duoc.pedidos360.pedidos.repository.PedidoRepository;

@Service
@Transactional(readOnly = true)
public class PedidoService {

    private static final List<EstadoPedido> EN_CURSO = List.of(EstadoPedido.PENDIENTE, EstadoPedido.CONFIRMADO,
            EstadoPedido.DESPACHADO);

    private final PedidoRepository repository;
    private final ProductosClient productosClient;

    public PedidoService(PedidoRepository repository, ProductosClient productosClient) {
        this.repository = repository;
        this.productosClient = productosClient;
    }

    public List<PedidoResponse> listar(Usuario usuario) {
        List<Pedido> pedidos = usuario.esAdmin()
                ? repository.findAllByOrderByCreadoEnDesc()
                : repository.findByClienteIdOrderByCreadoEnDesc(usuario.id());
        return pedidos.stream().map(PedidoResponse::from).toList();
    }

    public PedidoResponse obtener(Long id, Usuario usuario) {
        return PedidoResponse.from(buscarAutorizado(id, usuario));
    }

    /** Prices come from ms-productos, never from the client request. */
    @Transactional
    public PedidoResponse crear(CrearPedidoRequest request, Usuario usuario) {
        // Un cliente tiene como máximo un pedido en curso: el siguiente se puede hacer cuando se entregue o se cancele.
        repository.findFirstByClienteIdAndEstadoInOrderByCreadoEnDesc(usuario.id(), EN_CURSO).ifPresent(enCurso -> {
            throw new OperacionNoPermitidaException("Ya tienes un pedido en curso (#P360-%05d). Podrás hacer otro cuando se entregue o lo canceles."
                    .formatted(enCurso.getId()));
        });
        Pedido pedido = new Pedido(usuario.id(), usuario.username(), request.direccionEntrega().trim(),
                request.telefonoNormalizado(), request.latitud(), request.longitud(), request.metodoPago());
        for (ItemRequest item : request.items()) {
            ProductoDto producto = productosClient.obtener(item.productoId());
            if (producto.stock() != null && producto.stock() < item.cantidad()) {
                throw new ProductoNoDisponibleException("Stock insuficiente para " + producto.nombre());
            }
            pedido.agregarItem(new ItemPedido(producto.id(), producto.nombre(), item.cantidad(), producto.precio()));
        }
        return PedidoResponse.from(repository.save(pedido));
    }

    @Transactional
    public PedidoResponse cambiarEstado(Long id, EstadoPedido estado) {
        Pedido pedido = buscar(id);
        // Repetir el estado actual no cambia nada; saltarse pasos o retroceder no se permite.
        if (pedido.getEstado() != estado && !pedido.getEstado().puedePasarA(estado)) {
            throw new OperacionNoPermitidaException(
                    "Un pedido " + pedido.getEstado() + " no puede pasar a " + estado);
        }
        pedido.cambiarEstado(estado);
        // En efectivo, el repartidor cobra al entregar: el pago queda confirmado junto con la entrega.
        if (estado == EstadoPedido.ENTREGADO && pedido.getMetodoPago() == MetodoPago.EFECTIVO) {
            pedido.cambiarEstadoPago(EstadoPago.PAGADO);
        }
        return PedidoResponse.from(pedido);
    }

    @Transactional
    public PedidoResponse cambiarEstadoPago(Long id, EstadoPago estadoPago) {
        Pedido pedido = buscar(id);
        pedido.cambiarEstadoPago(estadoPago);
        return PedidoResponse.from(pedido);
    }

    @Transactional
    public PedidoResponse cancelar(Long id, Usuario usuario) {
        Pedido pedido = buscarAutorizado(id, usuario);
        if (pedido.getEstado() != EstadoPedido.PENDIENTE) {
            throw new OperacionNoPermitidaException("Solo se pueden cancelar pedidos PENDIENTE");
        }
        // Un pedido cobrado requiere devolver el dinero, así que solo la tienda puede anularlo.
        if (pedido.getEstadoPago() == EstadoPago.PAGADO) {
            throw new OperacionNoPermitidaException("El pedido ya fue pagado; para anularlo contacta a la tienda");
        }
        pedido.cambiarEstado(EstadoPedido.CANCELADO);
        return PedidoResponse.from(pedido);
    }

    /** The order if the caller owns it or is ADMIN; 403 otherwise. */
    public Pedido buscarAutorizado(Long id, Usuario usuario) {
        Pedido pedido = buscar(id);
        if (!usuario.esAdmin() && !pedido.perteneceA(usuario.id())) {
            throw new AccessDeniedException("El pedido pertenece a otro cliente");
        }
        return pedido;
    }

    private Pedido buscar(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Pedido " + id + " no existe"));
    }
}
