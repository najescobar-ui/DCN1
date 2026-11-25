package cl.duoc.pedidos360.streaming.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * @param total cantidad final de particiones. Kafka solo permite aumentarlas: reducirlas rompería el orden por key.
 */
public record AumentarParticionesRequest(
        @NotNull(message = "es obligatorio") @Min(value = 2, message = "debe ser al menos 2") @Max(value = 24, message = "no puede superar 24") Integer total) {
}
