package cl.duoc.pedidos360.mensajeria.exception;

/** RabbitMQ no responde (503). */
public class BrokerNoDisponibleException extends RuntimeException {
    public BrokerNoDisponibleException(String mensaje, Throwable causa) {
        super(mensaje, causa);
    }
}
