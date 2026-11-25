package cl.duoc.pedidos360.streaming.exception;

/** La operación choca con el estado actual (tópico protegido, ya existe, grupo con consumidores activos): 409. */
public class OperacionRechazadaException extends RuntimeException {

    public OperacionRechazadaException(String mensaje) {
        super(mensaje);
    }
}
