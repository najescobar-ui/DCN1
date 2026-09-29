package cl.duoc.pedidos360.productos.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ProductoRequest(
        @NotBlank @Size(max = 120) String nombre,
        @Size(max = 500) String descripcion,
        @NotBlank @Size(max = 40) String categoria,
        @NotNull @DecimalMin(value = "0.01") BigDecimal precio,
        @DecimalMin(value = "0.01") BigDecimal precioAnterior,
        @NotNull @Min(0) Integer stock,
        @Size(max = 300) String imagenUrl) {

    /** A previous price only makes sense when it is higher than the current one. */
    @AssertTrue(message = "precioAnterior debe ser mayor que precio")
    public boolean isPrecioAnteriorValido() {
        return precioAnterior == null || precio == null || precioAnterior.compareTo(precio) > 0;
    }
}
