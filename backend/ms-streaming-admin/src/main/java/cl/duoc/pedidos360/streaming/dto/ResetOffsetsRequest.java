package cl.duoc.pedidos360.streaming.dto;

import java.time.Instant;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * Mueve los offsets de un grupo en un tópico (para reprocesar o para saltarse lo acumulado).
 *
 * @param destino   INICIO (reprocesa todo lo retenido), FIN (descarta lo pendiente) o FECHA
 * @param desde     con destino FECHA, el primer evento a partir de este instante
 */
public record ResetOffsetsRequest(
        @NotBlank(message = "es obligatorio") @Pattern(regexp = Nombres.PATRON_GRUPO, message = Nombres.MENSAJE_GRUPO) String topico,
        @NotNull(message = "es obligatorio (INICIO, FIN o FECHA)") Destino destino,
        Instant desde) {

    public enum Destino {
        INICIO, FIN, FECHA
    }

    @AssertTrue(message = "desde es obligatorio cuando el destino es FECHA")
    public boolean isFechaCoherente() {
        return destino != Destino.FECHA || desde != null;
    }
}
