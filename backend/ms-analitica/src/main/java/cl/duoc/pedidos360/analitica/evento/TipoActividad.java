package cl.duoc.pedidos360.analitica.evento;

/** Hechos de negocio que ms-pedidos publica en el tópico de actividad. */
public enum TipoActividad {
    PEDIDO_CREADO,
    ESTADO_CAMBIADO,
    PAGO_APROBADO,
    PAGO_RECHAZADO
}
