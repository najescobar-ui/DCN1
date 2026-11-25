package cl.duoc.pedidos360.streaming.exception;

/** Kafka rechazó los parámetros (por ejemplo, más réplicas que brokers o menos particiones que las actuales): 400. */
public class OperacionInvalidaException extends RuntimeException {

    public OperacionInvalidaException(String mensaje) {
        super(mensaje);
    }
}
