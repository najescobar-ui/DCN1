package cl.duoc.pedidos360.notificaciones.mensajeria;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Nombres de exchanges, colas y bindings leídos del bloque "mensajeria" de application.yml.
 * Es la única fuente de estos nombres en el microservicio.
 */
@ConfigurationProperties("mensajeria")
public record MensajeriaProperties(
        Exchanges exchanges,
        Colas colas,
        String sufijoDlq,
        int maxIntentos,
        int prefetch,
        boolean simularFallos) {

    /**
     * @param eventos  exchange topic con los hechos de negocio que publica ms-pedidos
     * @param comandos exchange direct con trabajos dirigidos a un solo consumidor
     * @param dlx      dead letter exchange: recibe lo rechazado por cualquiera de las colas
     */
    public record Exchanges(String eventos, String comandos, String dlx) {
    }

    public record Colas(Cola notificaciones, Cola comprobantes, Cola auditoria) {
    }

    /**
     * @param nombre      nombre de la cola
     * @param routingKeys claves (o patrones, en un topic) con que la cola se enlaza a su exchange
     */
    public record Cola(String nombre, List<String> routingKeys) {
    }

    /** Nombre de la DLQ de una cola; también es la routing key con que el DLX la alcanza. */
    public String dlqDe(Cola cola) {
        return cola.nombre() + sufijoDlq;
    }
}
