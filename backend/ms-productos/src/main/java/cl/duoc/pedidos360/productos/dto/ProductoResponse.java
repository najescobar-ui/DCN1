package cl.duoc.pedidos360.productos.dto;

import java.math.BigDecimal;
import java.time.Instant;

import cl.duoc.pedidos360.productos.model.Producto;

public record ProductoResponse(Long id, String nombre, String descripcion, String categoria, BigDecimal precio,
        BigDecimal precioAnterior, Integer stock, String imagenUrl, Instant creadoEn) {

    public static ProductoResponse from(Producto producto) {
        return new ProductoResponse(producto.getId(), producto.getNombre(), producto.getDescripcion(),
                producto.getCategoria(), producto.getPrecio(), producto.getPrecioAnterior(), producto.getStock(),
                producto.getImagenUrl(), producto.getCreadoEn());
    }
}
