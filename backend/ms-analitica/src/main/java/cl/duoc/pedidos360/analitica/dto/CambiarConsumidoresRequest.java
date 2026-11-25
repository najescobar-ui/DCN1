package cl.duoc.pedidos360.analitica.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * @param consumidores 0 detiene el grupo; de 1 a 3, cuántos consumidores (más que particiones no sirve: sobrarían)
 */
public record CambiarConsumidoresRequest(
        @NotNull(message = "es obligatorio")
        @Min(value = 0, message = "no puede ser negativo")
        @Max(value = 3, message = "no puede superar la cantidad de particiones (3)") Integer consumidores) {
}
