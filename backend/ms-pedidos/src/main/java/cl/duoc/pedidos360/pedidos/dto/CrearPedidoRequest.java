package cl.duoc.pedidos360.pedidos.dto;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

public record CrearPedidoRequest(
        @NotEmpty @Size(max = 20) List<@Valid ItemRequest> items,
        @Size(max = 250) String direccionEntrega) {
}
