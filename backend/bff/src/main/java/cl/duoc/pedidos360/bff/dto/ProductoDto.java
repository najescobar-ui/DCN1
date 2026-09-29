package cl.duoc.pedidos360.bff.dto;

import java.math.BigDecimal;

public record ProductoDto(Long id, String nombre, BigDecimal precio, Integer stock) {
}
