package cl.duoc.pedidos360.streaming.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Eventos de prueba para la demo, publicados en pedidos.actividad.
 *
 * @param escenario VALIDO (un pedido completo: creado, pagado y entregado), INVALIDO (JSON ilegible),
 *                  INCOMPLETO (JSON sin pedidoId), FALLO_TRANSITORIO (válido, con el header que hace fallar a la
 *                  analítica) o LOTE (muchos pedidos de una vez, para ver el lag y la distribución por partición)
 * @param cantidad  pedidos del lote (solo LOTE)
 */
public record PublicarEventosRequest(
        @NotNull(message = "es obligatorio (VALIDO, INVALIDO, INCOMPLETO, FALLO_TRANSITORIO o LOTE)") Escenario escenario,
        @Min(value = 1, message = "debe ser al menos 1") @Max(value = 500, message = "no puede superar 500") Integer cantidad) {

    public enum Escenario {
        VALIDO, INVALIDO, INCOMPLETO, FALLO_TRANSITORIO, LOTE
    }
}
