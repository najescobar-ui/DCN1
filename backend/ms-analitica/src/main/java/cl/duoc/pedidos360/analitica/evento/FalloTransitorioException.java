package cl.duoc.pedidos360.analitica.evento;

/** Error recuperable (un recurso que no responde): el error handler reintenta antes de mandarlo a la DLT. */
public class FalloTransitorioException extends RuntimeException {

    public FalloTransitorioException(String mensaje) {
        super(mensaje);
    }
}
