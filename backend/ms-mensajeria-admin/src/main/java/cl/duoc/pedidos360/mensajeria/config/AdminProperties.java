package cl.duoc.pedidos360.mensajeria.config;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuración del administrador (bloque mensajeria.admin de application.yml).
 *
 * @param managementUrl    URL de la API HTTP de management de RabbitMQ
 * @param vhost            virtual host administrado
 * @param sufijoDlq        sufijo de las DLQ creadas por la API
 * @param dlx              dead letter exchange que alimenta esas DLQ
 * @param protegidos       colas y exchanges del sistema que no se pueden eliminar
 * @param prefijoReservado prefijo de los objetos internos de RabbitMQ
 * @param maxReproceso     tope de mensajes por operación de reproceso
 * @param headersDemo      headers de demostración que se quitan al reprocesar una DLQ
 */
@ConfigurationProperties("mensajeria.admin")
public record AdminProperties(String managementUrl, String vhost, String sufijoDlq, String dlx,
        List<String> protegidos, String prefijoReservado, int maxReproceso, List<String> headersDemo) {

    public boolean esProtegido(String nombre) {
        return protegidos.contains(nombre) || nombre.startsWith(prefijoReservado) || nombre.isEmpty();
    }
}
