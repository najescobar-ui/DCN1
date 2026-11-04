package cl.duoc.pedidos360.bff.enrutamiento;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import cl.duoc.pedidos360.bff.exception.ServicioNoDisponibleException;
import jakarta.servlet.http.HttpServletRequest;

/**
 * Punto de entrada único del backend: API Gateway entrega aquí todas las rutas /api/** y el BFF las reenvía
 * al microservicio que corresponde. Antes de llegar aquí, Spring Security ya validó el JWT (firma, issuer,
 * audience, vigencia) y la autorización por rol. El token se reenvía tal cual (token relay), así cada
 * microservicio vuelve a validarlo y aplica sus propias reglas de negocio.
 *
 * Las rutas /api/bff/* las atiende el propio BFF (BffController): Spring elige el mapeo más específico.
 */
@RestController
@EnableConfigurationProperties(EnrutamientoProperties.class)
public class EnrutadorController {

    private static final Logger log = LoggerFactory.getLogger(EnrutadorController.class);
    /** Headers que viajan al microservicio y de vuelta al cliente. */
    private static final List<String> HEADERS_PETICION = List.of(HttpHeaders.AUTHORIZATION, HttpHeaders.CONTENT_TYPE,
            HttpHeaders.ACCEPT);
    private static final Set<String> HEADERS_RESPUESTA = Set.of(HttpHeaders.CONTENT_TYPE.toLowerCase(),
            HttpHeaders.LOCATION.toLowerCase());

    private final EnrutamientoProperties props;
    private final HttpClient http;

    public EnrutadorController(EnrutamientoProperties props) {
        this.props = props;
        // Sin seguir redirecciones: el retorno de Webpay responde 302 hacia el sitio y debe llegar al navegador.
        this.http = HttpClient.newBuilder()
                .connectTimeout(props.timeout())
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    @RequestMapping("/api/**")
    public ResponseEntity<byte[]> reenviar(HttpServletRequest peticion, @RequestBody(required = false) byte[] cuerpo)
            throws InterruptedException {
        String path = peticion.getRequestURI();
        EnrutamientoProperties.Ruta ruta = props.resolver(path)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Ruta no encontrada: " + path));
        String query = peticion.getQueryString();
        URI destino = URI.create(ruta.destino() + path + (query == null ? "" : "?" + query));

        HttpRequest.Builder salida = HttpRequest.newBuilder(destino)
                .timeout(props.timeout())
                .method(peticion.getMethod(), cuerpo == null || cuerpo.length == 0
                        ? HttpRequest.BodyPublishers.noBody()
                        : HttpRequest.BodyPublishers.ofByteArray(cuerpo));
        for (String nombre : HEADERS_PETICION) {
            String valor = peticion.getHeader(nombre);
            if (valor != null) {
                salida.header(nombre, valor);
            }
        }

        HttpResponse<byte[]> respuesta;
        try {
            respuesta = http.send(salida.build(), HttpResponse.BodyHandlers.ofByteArray());
        } catch (IOException ex) {
            log.warn("No se pudo contactar {} para {} {}: {}", ruta.destino(), peticion.getMethod(), path, ex.getMessage());
            throw new ServicioNoDisponibleException("Servicio no disponible para " + path);
        }

        HttpHeaders headers = new HttpHeaders();
        respuesta.headers().map().forEach((nombre, valores) -> {
            if (HEADERS_RESPUESTA.contains(nombre.toLowerCase())) {
                headers.addAll(nombre, valores);
            }
        });
        log.debug("{} {} -> {} [{}]", peticion.getMethod(), path, ruta.destino(), respuesta.statusCode());
        return ResponseEntity.status(respuesta.statusCode()).headers(headers).body(respuesta.body());
    }
}
