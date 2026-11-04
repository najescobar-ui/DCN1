package cl.duoc.pedidos360.pedidos.evento;

/** Hechos de negocio que ms-pedidos comunica al resto del sistema. */
public enum TipoEvento {
    PEDIDO_CREADO,
    ESTADO_CAMBIADO,
    PAGO_APROBADO,
    PAGO_RECHAZADO
}
