package cl.duoc.pedidos360.mensajeria.exception;

/** La cola, exchange o binding pedido no existe (404). */
public class RecursoNoEncontradoException extends RuntimeException {
    public RecursoNoEncontradoException(String mensaje) {
        super(mensaje);
    }
}
