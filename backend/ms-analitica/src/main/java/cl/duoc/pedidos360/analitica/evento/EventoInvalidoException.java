package cl.duoc.pedidos360.analitica.evento;

/**
 * El evento llegó pero no se puede procesar (faltan datos obligatorios). Reintentarlo daría el mismo error, así
 * que el error handler lo manda directo al tópico de errores, sin reintentos.
 */
public class EventoInvalidoException extends RuntimeException {

    public EventoInvalidoException(String mensaje) {
        super(mensaje);
    }
}
