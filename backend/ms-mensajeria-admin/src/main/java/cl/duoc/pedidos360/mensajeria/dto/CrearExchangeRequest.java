package cl.duoc.pedidos360.mensajeria.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * @param nombre  nombre del exchange
 * @param tipo    DIRECT, TOPIC, FANOUT o HEADERS
 * @param durable si sobrevive a un reinicio del broker (por defecto true)
 */
public record CrearExchangeRequest(
        @NotBlank @Pattern(regexp = Nombres.PATRON_NOMBRE, message = Nombres.MENSAJE_NOMBRE) String nombre,
        @NotNull(message = "es obligatorio (DIRECT, TOPIC, FANOUT o HEADERS)") TipoExchange tipo,
        Boolean durable) {

    public boolean esDurable() {
        return durable == null || durable;
    }
}
