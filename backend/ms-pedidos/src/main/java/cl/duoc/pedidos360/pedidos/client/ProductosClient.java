package cl.duoc.pedidos360.pedidos.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import cl.duoc.pedidos360.pedidos.exception.ProductoNoDisponibleException;
import cl.duoc.pedidos360.pedidos.exception.ServicioNoDisponibleException;

/**
 * Calls ms-productos forwarding the caller's access token (token relay),
 * so ms-productos applies its own JWT validation too.
 */
@Component
public class ProductosClient {

    private final RestClient restClient;

    public ProductosClient(RestClient.Builder builder, @Value("${app.productos-url}") String productosUrl) {
        this.restClient = builder.baseUrl(productosUrl)
                .requestInterceptor((request, body, execution) -> {
                    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
                    if (auth instanceof JwtAuthenticationToken jwtAuth) {
                        request.getHeaders().setBearerAuth(jwtAuth.getToken().getTokenValue());
                    }
                    return execution.execute(request, body);
                })
                .defaultHeader(HttpHeaders.ACCEPT, "application/json")
                .build();
    }

    public ProductoDto obtener(Long productoId) {
        try {
            return restClient.get().uri("/api/productos/{id}", productoId).retrieve().body(ProductoDto.class);
        } catch (RestClientResponseException ex) {
            if (ex.getStatusCode().isSameCodeAs(HttpStatus.NOT_FOUND)) {
                throw new ProductoNoDisponibleException("Producto " + productoId + " no existe");
            }
            throw new ServicioNoDisponibleException("ms-productos respondio " + ex.getStatusCode().value());
        } catch (RestClientException ex) {
            throw new ServicioNoDisponibleException("No se pudo contactar ms-productos");
        }
    }
}
