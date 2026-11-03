package cl.duoc.pedidos360.mensajeria.config;

import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.boot.amqp.autoconfigure.RabbitProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * Infraestructura del administrador: RabbitAdmin (protocolo AMQP, para declarar y eliminar) y un cliente
 * HTTP de la API de management (para consultar). Solo RabbitAdminService los usa.
 */
@Configuration
@EnableConfigurationProperties(AdminProperties.class)
public class RabbitAdminConfig {

    @Bean
    RabbitAdmin rabbitAdmin(ConnectionFactory connectionFactory) {
        RabbitAdmin admin = new RabbitAdmin(connectionFactory);
        // Si una declaración falla (por ejemplo, parámetros distintos a los existentes), se informa al llamador.
        admin.setIgnoreDeclarationExceptions(false);
        return admin;
    }

    @Bean
    RestClient managementClient(AdminProperties props, RabbitProperties rabbit) {
        return RestClient.builder()
                .baseUrl(props.managementUrl())
                .defaultHeaders(h -> h.setBasicAuth(rabbit.getUsername(), rabbit.getPassword()))
                .build();
    }
}
