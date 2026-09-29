package cl.duoc.pedidos360.productos.model;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

@Entity
@Table(name = "productos")
public class Producto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String nombre;

    @Column(length = 500)
    private String descripcion;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal precio;

    /** Price before the current discount; null when the product is not on sale. */
    @Column(name = "precio_anterior", precision = 12, scale = 2)
    private BigDecimal precioAnterior;

    @Column(nullable = false)
    private Integer stock;

    @Column(length = 40)
    private String categoria;

    @Column(name = "imagen_url", length = 300)
    private String imagenUrl;

    @Column(nullable = false)
    private boolean activo = true;

    @Column(name = "creado_en", nullable = false, updatable = false)
    private Instant creadoEn;

    protected Producto() {
    }

    public Producto(String nombre, String descripcion, String categoria, BigDecimal precio,
            BigDecimal precioAnterior, Integer stock, String imagenUrl) {
        actualizar(nombre, descripcion, categoria, precio, precioAnterior, stock, imagenUrl);
    }

    @PrePersist
    void onCreate() {
        creadoEn = Instant.now();
    }

    public void actualizar(String nombre, String descripcion, String categoria, BigDecimal precio,
            BigDecimal precioAnterior, Integer stock, String imagenUrl) {
        this.nombre = nombre;
        this.descripcion = descripcion;
        this.categoria = categoria;
        this.precio = precio;
        this.precioAnterior = precioAnterior;
        this.stock = stock;
        this.imagenUrl = imagenUrl;
    }

    public void desactivar() {
        this.activo = false;
    }

    public Long getId() {
        return id;
    }

    public String getNombre() {
        return nombre;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public BigDecimal getPrecio() {
        return precio;
    }

    public BigDecimal getPrecioAnterior() {
        return precioAnterior;
    }

    public String getCategoria() {
        return categoria;
    }

    public String getImagenUrl() {
        return imagenUrl;
    }

    public Integer getStock() {
        return stock;
    }

    public boolean isActivo() {
        return activo;
    }

    public Instant getCreadoEn() {
        return creadoEn;
    }
}
