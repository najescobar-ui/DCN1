package cl.duoc.pedidos360.pedidos.mensajeria;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Streaming con Kafka (bloque "streaming" de application.yml).
 *
 * @param habilitado si es false no se publica en Kafka (por ejemplo, en local sin brokers)
 * @param topico     tópico de los eventos de dominio de los pedidos
 */
@ConfigurationProperties("streaming")
public record StreamingProperties(boolean habilitado, String topico) {
}
