package cl.duoc.pedidos360.streaming.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * Cambios de configuración de un tópico; solo se aplican los campos que vienen.
 */
public record ModificarTopicoRequest(
        @Min(value = 60_000, message = "debe ser de al menos 1 minuto") @Max(value = 2_592_000_000L, message = "no puede superar 30 días") Long retencionMs,
        @Min(value = 1_048_576, message = "debe ser de al menos 1 MiB") @Max(value = 1_073_741_824L, message = "no puede superar 1 GiB") Long retencionBytes,
        CleanupPolicy cleanupPolicy,
        @Min(value = 1, message = "debe ser al menos 1") @Max(value = 3, message = "no puede superar 3") Integer minInsync) {

    @AssertTrue(message = "indica al menos un cambio (retencionMs, retencionBytes, cleanupPolicy o minInsync)")
    public boolean isAlgunCambio() {
        return retencionMs != null || retencionBytes != null || cleanupPolicy != null || minInsync != null;
    }
}
