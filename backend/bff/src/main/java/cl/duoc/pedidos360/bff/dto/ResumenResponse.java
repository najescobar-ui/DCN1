package cl.duoc.pedidos360.bff.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public record ResumenResponse(UsuarioResponse usuario, int productosDisponibles, int totalPedidos,
        BigDecimal montoTotal, Map<String, Long> pedidosPorEstado, List<PedidoDto> ultimosPedidos,
        List<ProductoDto> productosDestacados) {
}
