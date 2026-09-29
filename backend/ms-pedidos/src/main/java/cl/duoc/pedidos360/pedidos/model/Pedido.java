package cl.duoc.pedidos360.pedidos.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

@Entity
@Table(name = "pedidos")
public class Pedido {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Cognito "sub" of the customer who placed the order. */
    @Column(name = "cliente_id", nullable = false, length = 64)
    private String clienteId;

    @Column(name = "cliente_username", length = 128)
    private String clienteUsername;

    @Column(name = "direccion_entrega", length = 250)
    private String direccionEntrega;

    @Column(name = "telefono_contacto", length = 20)
    private String telefonoContacto;

    private Double latitud;

    private Double longitud;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoPedido estado = EstadoPedido.PENDIENTE;

    // Payment columns are nullable in the database so rows created before payments existed stay valid.
    @Enumerated(EnumType.STRING)
    @Column(name = "metodo_pago", length = 20)
    private MetodoPago metodoPago;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado_pago", length = 20)
    private EstadoPago estadoPago = EstadoPago.PENDIENTE;

    /** Webpay transaction token of the last payment attempt. */
    @Column(name = "pago_token", length = 100, unique = true)
    private String pagoToken;

    @Column(name = "pago_autorizacion", length = 20)
    private String pagoAutorizacion;

    /** Last 4 digits of the card, as returned by Webpay. */
    @Column(name = "pago_tarjeta", length = 8)
    private String pagoTarjeta;

    /** Frontend origin that started the payment, to send the customer back after Webpay. */
    @Column(name = "pago_origen", length = 200)
    private String pagoOrigen;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal total = BigDecimal.ZERO;

    @OneToMany(mappedBy = "pedido", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ItemPedido> items = new ArrayList<>();

    @Column(name = "creado_en", nullable = false, updatable = false)
    private Instant creadoEn;

    @Column(name = "actualizado_en", nullable = false)
    private Instant actualizadoEn;

    protected Pedido() {
    }

    public Pedido(String clienteId, String clienteUsername, String direccionEntrega, String telefonoContacto,
            Double latitud, Double longitud, MetodoPago metodoPago) {
        this.clienteId = clienteId;
        this.clienteUsername = clienteUsername;
        this.direccionEntrega = direccionEntrega;
        this.telefonoContacto = telefonoContacto;
        this.latitud = latitud;
        this.longitud = longitud;
        this.metodoPago = metodoPago;
    }

    @PrePersist
    void onCreate() {
        creadoEn = Instant.now();
        actualizadoEn = creadoEn;
    }

    @PreUpdate
    void onUpdate() {
        actualizadoEn = Instant.now();
    }

    public void agregarItem(ItemPedido item) {
        item.setPedido(this);
        items.add(item);
        total = total.add(item.getSubtotal());
    }

    public void cambiarEstado(EstadoPedido nuevoEstado) {
        this.estado = nuevoEstado;
    }

    public void iniciarPagoWebpay(String token, String origen) {
        this.pagoToken = token;
        this.pagoOrigen = origen;
        this.estadoPago = EstadoPago.PENDIENTE;
    }

    public void registrarPagoAprobado(String autorizacion, String tarjeta) {
        this.estadoPago = EstadoPago.PAGADO;
        this.pagoAutorizacion = autorizacion;
        this.pagoTarjeta = tarjeta;
    }

    public void cambiarEstadoPago(EstadoPago estadoPago) {
        this.estadoPago = estadoPago;
    }

    public boolean perteneceA(String clienteId) {
        return this.clienteId.equals(clienteId);
    }

    public Long getId() {
        return id;
    }

    public String getClienteId() {
        return clienteId;
    }

    public String getClienteUsername() {
        return clienteUsername;
    }

    public String getDireccionEntrega() {
        return direccionEntrega;
    }

    public String getTelefonoContacto() {
        return telefonoContacto;
    }

    public Double getLatitud() {
        return latitud;
    }

    public Double getLongitud() {
        return longitud;
    }

    public MetodoPago getMetodoPago() {
        return metodoPago;
    }

    public EstadoPago getEstadoPago() {
        return estadoPago;
    }

    public String getPagoToken() {
        return pagoToken;
    }

    public String getPagoAutorizacion() {
        return pagoAutorizacion;
    }

    public String getPagoTarjeta() {
        return pagoTarjeta;
    }

    public String getPagoOrigen() {
        return pagoOrigen;
    }

    public EstadoPedido getEstado() {
        return estado;
    }

    public BigDecimal getTotal() {
        return total;
    }

    public List<ItemPedido> getItems() {
        return items;
    }

    public Instant getCreadoEn() {
        return creadoEn;
    }

    public Instant getActualizadoEn() {
        return actualizadoEn;
    }
}
