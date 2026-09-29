package cl.duoc.pedidos360.pedidos.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record ItemRequest(@NotNull Long productoId, @NotNull @Min(1) @Max(99) Integer cantidad) {
}
