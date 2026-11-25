package cl.duoc.pedidos360.analitica.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.config.TopicConfig;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * Declaración de los tópicos. KafkaAdmin los crea al arrancar si no existen y, si existen con otra configuración
 * (retención, cleanup policy, min.insync.replicas), la corrige (spring.kafka.admin.modify-topic-configs). Así la
 * configuración de los tópicos vive en application.yml y se aplica sola en cualquier ambiente.
 *
 * <pre>
 * pedidos.actividad      3 particiones · 3 réplicas · delete 7 días        eventos de dominio (key = pedidoId)
 * ventas.metricas        3 particiones · 3 réplicas · compact,delete 24 h  métricas por ventana (key = ventana)
 * pedidos.actividad.DLT  3 particiones · 3 réplicas · delete 14 días       eventos que no se pudieron procesar
 * </pre>
 */
@Configuration
@EnableConfigurationProperties(StreamingProperties.class)
public class KafkaTopicosConfig {

    @Bean
    NewTopic topicoActividad(StreamingProperties props) {
        return topico(props.topicos().actividad());
    }

    @Bean
    NewTopic topicoMetricas(StreamingProperties props) {
        return topico(props.topicos().metricas());
    }

    @Bean
    NewTopic topicoErrores(StreamingProperties props) {
        return topico(props.topicos().errores());
    }

    static NewTopic topico(StreamingProperties.Topico t) {
        return TopicBuilder.name(t.nombre())
                .partitions(t.particiones())
                .replicas(t.replicas())
                .config(TopicConfig.CLEANUP_POLICY_CONFIG, t.cleanupPolicy())
                .config(TopicConfig.RETENTION_MS_CONFIG, Long.toString(t.retencionMs()))
                .config(TopicConfig.RETENTION_BYTES_CONFIG, Long.toString(t.retencionBytes()))
                .config(TopicConfig.SEGMENT_MS_CONFIG, Long.toString(t.segmentoMs()))
                .config(TopicConfig.MIN_IN_SYNC_REPLICAS_CONFIG, Integer.toString(t.minInsync()))
                .build();
    }
}
