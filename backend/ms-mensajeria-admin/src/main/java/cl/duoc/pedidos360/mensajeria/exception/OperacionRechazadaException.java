package cl.duoc.pedidos360.mensajeria.exception;

/**
 * La operación choca con el estado actual (409): el objeto ya existe, es parte de la topología protegida
 * del sistema o RabbitMQ rechazó la configuración.
 */
public class OperacionRechazadaException extends RuntimeException {
    public OperacionRechazadaException(String mensaje) {
        super(mensaje);
    }
}
