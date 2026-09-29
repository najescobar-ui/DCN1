package cl.duoc.pedidos360.pedidos.dto;

import cl.duoc.pedidos360.pedidos.model.EstadoPedido;
import jakarta.validation.constraints.NotNull;

public record CambiarEstadoRequest(@NotNull EstadoPedido estado) {
}
