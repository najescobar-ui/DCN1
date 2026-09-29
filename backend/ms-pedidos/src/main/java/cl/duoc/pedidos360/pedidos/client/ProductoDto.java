package cl.duoc.pedidos360.pedidos.client;

import java.math.BigDecimal;

/** Subset of the ms-productos response that this service needs. */
public record ProductoDto(Long id, String nombre, BigDecimal precio, Integer stock) {
}
