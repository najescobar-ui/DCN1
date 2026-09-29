package cl.duoc.pedidos360.bff.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record PedidoDto(Long id, String clienteUsername, String estado, BigDecimal total, Instant creadoEn) {
}
