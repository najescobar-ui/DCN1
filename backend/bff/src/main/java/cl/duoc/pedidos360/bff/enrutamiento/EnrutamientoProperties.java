package cl.duoc.pedidos360.bff.enrutamiento;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Tabla de enrutamiento del BFF (bloque app.enrutamiento de application.yml): qué microservicio atiende
 * cada prefijo de ruta.
 *
 * @param timeout tiempo máximo de respuesta de un microservicio
 * @param rutas   prefijo de la ruta pública → URL base del microservicio
 */
@ConfigurationProperties("app.enrutamiento")
public record EnrutamientoProperties(Duration timeout, List<Ruta> rutas) {

    public record Ruta(String prefijo, String destino) {
    }

    /** Microservicio que atiende la ruta, o vacío si ningún prefijo coincide. */
    public Optional<Ruta> resolver(String path) {
        return rutas.stream()
                .filter(r -> path.equals(r.prefijo()) || path.startsWith(r.prefijo() + "/"))
                .findFirst();
    }
}
