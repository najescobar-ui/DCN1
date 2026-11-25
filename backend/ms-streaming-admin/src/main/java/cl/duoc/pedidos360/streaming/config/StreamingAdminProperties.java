package cl.duoc.pedidos360.streaming.config;

import java.time.Duration;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Parámetros del administrador (bloque streaming.admin de application.yml).
 *
 * @param timeout            tiempo máximo de cada operación contra el clúster
 * @param topicosProtegidos  tópicos del sistema: se pueden ajustar, no eliminar
 * @param prefijoInterno     prefijo de los tópicos internos de Kafka (solo lectura)
 * @param topicoDemo         tópico al que publica la demo de eventos
 * @param headerSimularFallo header que hace fallar de forma transitoria al grupo analitica-ventas
 * @param maxLote            máximo de eventos por lote en la demo
 */
@ConfigurationProperties("streaming.admin")
public record StreamingAdminProperties(Duration timeout, List<String> topicosProtegidos, String prefijoInterno,
        String topicoDemo, String headerSimularFallo, int maxLote) {

    public boolean esInterno(String topico) {
        return topico.startsWith(prefijoInterno);
    }

    public boolean esProtegido(String topico) {
        return esInterno(topico) || topicosProtegidos.contains(topico);
    }
}
