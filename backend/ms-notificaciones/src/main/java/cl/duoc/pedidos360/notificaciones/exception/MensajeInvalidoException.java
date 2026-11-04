package cl.duoc.pedidos360.notificaciones.exception;

/**
 * Error no recuperable: el mensaje no se puede leer o le faltan datos obligatorios. Reintentarlo daría el
 * mismo resultado, así que va directo a la DLQ.
 */
public class MensajeInvalidoException extends RuntimeException {

    public MensajeInvalidoException(String mensaje, Throwable causa) {
        super(mensaje, causa);
    }

    public MensajeInvalidoException(String mensaje) {
        super(mensaje);
    }
}
