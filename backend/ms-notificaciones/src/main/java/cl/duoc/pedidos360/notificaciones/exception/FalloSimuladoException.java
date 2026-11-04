package cl.duoc.pedidos360.notificaciones.exception;

/** Error recuperable forzado con el header x-simular-fallo, solo para demostrar reintentos y DLQ. */
public class FalloSimuladoException extends RuntimeException {

    public FalloSimuladoException(String mensaje) {
        super(mensaje);
    }
}
