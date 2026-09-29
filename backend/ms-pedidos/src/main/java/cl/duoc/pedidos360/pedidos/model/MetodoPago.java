package cl.duoc.pedidos360.pedidos.model;

public enum MetodoPago {
    /** Credit/debit card through Transbank Webpay Plus. */
    TARJETA,
    /** Bank transfer; the customer sends the receipt and an admin confirms it. */
    TRANSFERENCIA,
    /** Cash on delivery. */
    EFECTIVO
}
