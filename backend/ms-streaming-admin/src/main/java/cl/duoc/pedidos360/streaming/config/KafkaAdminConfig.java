package cl.duoc.pedidos360.streaming.config;

import java.util.HashMap;
import java.util.Map;

import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaAdmin;

/**
 * Cliente de administración de Kafka. Lo usa solo KafkaAdminService; los controladores no lo conocen.
 */
@Configuration
@EnableConfigurationProperties(StreamingAdminProperties.class)
public class KafkaAdminConfig {

    @Bean(destroyMethod = "close")
    Admin kafkaAdminClient(KafkaAdmin kafkaAdmin, StreamingAdminProperties props) {
        Map<String, Object> config = new HashMap<>(kafkaAdmin.getConfigurationProperties());
        int timeout = (int) props.timeout().toMillis();
        config.put(AdminClientConfig.REQUEST_TIMEOUT_MS_CONFIG, timeout);
        config.put(AdminClientConfig.DEFAULT_API_TIMEOUT_MS_CONFIG, timeout);
        return Admin.create(config);
    }
}
