package cl.duoc.pedidos360.streaming.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * Datos para crear un tópico.
 *
 * @param particiones    cuántas particiones (paralelismo máximo de un grupo)
 * @param replicacion    copias de cada partición (como máximo, la cantidad de brokers: 3)
 * @param minInsync      réplicas que deben confirmar una escritura con acks=all (opcional, por defecto 2)
 * @param retencionMs    retention.ms (opcional)
 * @param retencionBytes retention.bytes por partición (opcional)
 * @param cleanupPolicy  DELETE, COMPACT o COMPACT_DELETE (opcional, por defecto DELETE)
 */
public record CrearTopicoRequest(
        @NotBlank(message = "es obligatorio") @Pattern(regexp = Nombres.PATRON_TOPICO, message = Nombres.MENSAJE_TOPICO) String nombre,
        @NotNull(message = "es obligatorio") @Min(value = 1, message = "debe ser al menos 1") @Max(value = 24, message = "no puede superar 24") Integer particiones,
        @NotNull(message = "es obligatorio") @Min(value = 1, message = "debe ser al menos 1") @Max(value = 3, message = "no puede superar la cantidad de brokers (3)") Short replicacion,
        @Min(value = 1, message = "debe ser al menos 1") @Max(value = 3, message = "no puede superar 3") Integer minInsync,
        @Min(value = 60_000, message = "debe ser de al menos 1 minuto") @Max(value = 2_592_000_000L, message = "no puede superar 30 días") Long retencionMs,
        @Min(value = 1_048_576, message = "debe ser de al menos 1 MiB") @Max(value = 1_073_741_824L, message = "no puede superar 1 GiB") Long retencionBytes,
        CleanupPolicy cleanupPolicy) {

    /** Si min.insync.replicas supera las réplicas, ninguna escritura con acks=all podría confirmarse. */
    @AssertTrue(message = "minInsync no puede ser mayor que replicacion")
    public boolean isMinInsyncCoherente() {
        return minInsync == null || replicacion == null || minInsync <= replicacion;
    }
}
