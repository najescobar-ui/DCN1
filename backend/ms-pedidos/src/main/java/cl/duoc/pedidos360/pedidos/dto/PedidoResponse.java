package cl.duoc.pedidos360.pedidos.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import cl.duoc.pedidos360.pedidos.model.EstadoPedido;
import cl.duoc.pedidos360.pedidos.model.ItemPedido;
import cl.duoc.pedidos360.pedidos.model.Pedido;

public record PedidoResponse(Long id, String clienteId, String clienteUsername, String direccionEntrega,
        EstadoPedido estado, BigDecimal total, List<Item> items, Instant creadoEn, Instant actualizadoEn) {

    public record Item(Long productoId, String nombreProducto, Integer cantidad, BigDecimal precioUnitario,
            BigDecimal subtotal) {

        static Item from(ItemPedido item) {
            return new Item(item.getProductoId(), item.getNombreProducto(), item.getCantidad(),
                    item.getPrecioUnitario(), item.getSubtotal());
        }
    }

    public static PedidoResponse from(Pedido pedido) {
        return new PedidoResponse(pedido.getId(), pedido.getClienteId(), pedido.getClienteUsername(),
                pedido.getDireccionEntrega(), pedido.getEstado(), pedido.getTotal(),
                pedido.getItems().stream().map(Item::from).toList(), pedido.getCreadoEn(), pedido.getActualizadoEn());
    }
}
