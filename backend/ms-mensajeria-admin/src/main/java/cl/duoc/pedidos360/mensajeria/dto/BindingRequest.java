package cl.duoc.pedidos360.mensajeria.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * Enlace exchange → cola. Se usa para crear (POST) y para eliminar (DELETE) un binding.
 *
 * @param exchange   exchange de origen
 * @param cola       cola de destino
 * @param routingKey clave o patrón; vacío es válido (fanout)
 */
public record BindingRequest(
        @NotBlank @Pattern(regexp = Nombres.PATRON_NOMBRE, message = Nombres.MENSAJE_NOMBRE) String exchange,
        @NotBlank @Pattern(regexp = Nombres.PATRON_NOMBRE, message = Nombres.MENSAJE_NOMBRE) String cola,
        @NotNull(message = "es obligatorio (puede ser vacío)") @Pattern(regexp = Nombres.PATRON_ROUTING_KEY, message = Nombres.MENSAJE_ROUTING_KEY) String routingKey) {
}
