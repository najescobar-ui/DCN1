package cl.duoc.pedidos360.bff.client;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import cl.duoc.pedidos360.bff.dto.PedidoDto;
import cl.duoc.pedidos360.bff.dto.ProductoDto;
import cl.duoc.pedidos360.bff.exception.ServicioNoDisponibleException;

@Component
public class BackendClient {

    private final RestClient productos;
    private final RestClient pedidos;

    public BackendClient(RestClient.Builder builder,
            @Value("${app.productos-url}") String productosUrl,
            @Value("${app.pedidos-url}") String pedidosUrl) {
        RestClient.Builder relaying = builder.requestInterceptor(new TokenRelayInterceptor());
        this.productos = relaying.clone().baseUrl(productosUrl).build();
        this.pedidos = relaying.clone().baseUrl(pedidosUrl).build();
    }

    public List<ProductoDto> productos() {
        return get(productos, "/api/productos", new ParameterizedTypeReference<List<ProductoDto>>() { }, "ms-productos");
    }

    public List<PedidoDto> pedidos() {
        return get(pedidos, "/api/pedidos", new ParameterizedTypeReference<List<PedidoDto>>() { }, "ms-pedidos");
    }

    private static <T> T get(RestClient client, String path, ParameterizedTypeReference<T> type, String service) {
        try {
            return client.get().uri(path).retrieve().body(type);
        } catch (RestClientException ex) {
            throw new ServicioNoDisponibleException("Error consultando " + service + ": " + ex.getMessage());
        }
    }
}
