package cl.duoc.pedidos360.pedidos.dto;

import java.util.List;

import cl.duoc.pedidos360.pedidos.model.MetodoPago;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CrearPedidoRequest(
        @NotEmpty @Size(max = 20) List<@Valid ItemRequest> items,
        @NotBlank @Size(min = 5, max = 250) String direccionEntrega,
        /** Chilean mobile number: 9 digits starting with 9, optionally prefixed by +56. */
        @NotBlank @Pattern(regexp = "^(\\+?56)?\\s?9\\s?\\d{4}\\s?\\d{4}$", message = "debe ser un celular chileno, ej: +56 9 1234 5678")
        String telefono,
        @DecimalMin("-90") @DecimalMax("90") Double latitud,
        @DecimalMin("-180") @DecimalMax("180") Double longitud,
        @NotNull MetodoPago metodoPago) {

    /** Normalizes the phone to +569XXXXXXXX. */
    public String telefonoNormalizado() {
        String digits = telefono.replaceAll("\\D", "");
        return "+56" + digits.substring(digits.length() - 9);
    }
}
