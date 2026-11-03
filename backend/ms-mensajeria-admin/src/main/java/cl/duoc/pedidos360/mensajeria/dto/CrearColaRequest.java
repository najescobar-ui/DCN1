package cl.duoc.pedidos360.mensajeria.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * Datos para crear una cola.
 *
 * @param nombre         nombre de la cola
 * @param tipo           QUORUM (replicada) o CLASSIC
 * @param conDlq         si es true se crea también {nombre}.dlq y la cola queda enlazada al DLX
 * @param ttlMs          vida máxima de un mensaje en la cola (opcional)
 * @param maxMensajes    largo máximo de la cola (opcional)
 * @param limiteEntregas entregas fallidas antes de mandar a la DLQ (solo quorum, opcional)
 */
public record CrearColaRequest(
        @NotBlank @Pattern(regexp = Nombres.PATRON_NOMBRE, message = Nombres.MENSAJE_NOMBRE) String nombre,
        @NotNull(message = "es obligatorio (QUORUM o CLASSIC)") TipoCola tipo,
        Boolean conDlq,
        @Min(value = 1000, message = "debe ser de al menos 1000 ms") @Max(value = 604_800_000, message = "no puede superar 7 días") Long ttlMs,
        @Min(value = 1, message = "debe ser mayor que 0") @Max(value = 1_000_000, message = "no puede superar 1.000.000") Integer maxMensajes,
        @Min(value = 1, message = "debe ser mayor que 0") @Max(value = 100, message = "no puede superar 100") Integer limiteEntregas) {

    public boolean conDlqActivo() {
        return Boolean.TRUE.equals(conDlq);
    }

    /** El límite de entregas es una función de las colas quorum; en una clásica sería ignorado en silencio. */
    @AssertTrue(message = "limiteEntregas solo aplica a colas QUORUM")
    public boolean isLimiteEntregasCoherente() {
        return limiteEntregas == null || tipo != TipoCola.CLASSIC;
    }

    /** Una cola que termina en el sufijo de DLQ no puede pedir su propia DLQ (quedaría name.dlq.dlq). */
    @AssertTrue(message = "una cola .dlq no puede tener a su vez una DLQ")
    public boolean isDlqCoherente() {
        return !conDlqActivo() || nombre == null || !nombre.endsWith(".dlq");
    }
}
