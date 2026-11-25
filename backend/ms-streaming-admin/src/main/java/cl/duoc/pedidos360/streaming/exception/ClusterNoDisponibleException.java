package cl.duoc.pedidos360.streaming.exception;

public class ClusterNoDisponibleException extends RuntimeException {

    public ClusterNoDisponibleException(String mensaje, Throwable causa) {
        super(mensaje, causa);
    }
}
