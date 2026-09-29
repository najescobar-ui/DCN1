package cl.duoc.pedidos360.productos.dto;

import java.math.BigDecimal;
import java.time.Instant;

import cl.duoc.pedidos360.productos.model.Producto;

public record ProductoResponse(Long id, String nombre, String descripcion, BigDecimal precio, Integer stock,
        Instant creadoEn) {

    public static ProductoResponse from(Producto producto) {
        return new ProductoResponse(producto.getId(), producto.getNombre(), producto.getDescripcion(),
                producto.getPrecio(), producto.getStock(), producto.getCreadoEn());
    }
}
