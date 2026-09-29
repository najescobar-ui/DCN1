package cl.duoc.pedidos360.pedidos.dto;

import cl.duoc.pedidos360.pedidos.model.EstadoPago;
import jakarta.validation.constraints.NotNull;

public record CambiarEstadoPagoRequest(@NotNull EstadoPago estadoPago) {
}
