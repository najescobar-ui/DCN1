package cl.duoc.pedidos360.mensajeria.dto;

import java.util.Map;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Mensaje de prueba para verificar una ruta de mensajería.
 *
 * @param exchange   exchange de destino
 * @param routingKey clave de enrutamiento
 * @param cuerpo     contenido (se envía tal cual, como JSON)
 * @param headers    headers opcionales (por ejemplo x-simular-fallo para la demostración de reintentos)
 */
public record PublicarMensajeRequest(
        @NotBlank @Pattern(regexp = Nombres.PATRON_NOMBRE, message = Nombres.MENSAJE_NOMBRE) String exchange,
        @NotNull @Pattern(regexp = Nombres.PATRON_ROUTING_KEY, message = Nombres.MENSAJE_ROUTING_KEY) String routingKey,
        @NotNull(message = "es obligatorio") @Size(max = 10_000, message = "no puede superar 10.000 caracteres") String cuerpo,
        @Size(max = 10, message = "admite hasta 10 headers") Map<@Pattern(regexp = "^[A-Za-z0-9-]{1,50}$", message = "nombre de header inválido") String, @Size(max = 200) String> headers) {
}
