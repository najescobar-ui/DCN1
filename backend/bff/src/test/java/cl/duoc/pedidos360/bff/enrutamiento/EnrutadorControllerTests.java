package cl.duoc.pedidos360.bff.enrutamiento;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import com.sun.net.httpserver.HttpServer;

/**
 * El BFF como punto de entrada: reenvía al microservicio según el prefijo, con el token, y aplica la
 * autorización por rol antes de reenviar. Un servidor HTTP local hace de microservicio.
 */
@SpringBootTest
@AutoConfigureMockMvc
class EnrutadorControllerTests {

    private static final SimpleGrantedAuthority CLIENTE = new SimpleGrantedAuthority("ROLE_CLIENTE");
    private static final SimpleGrantedAuthority ADMIN = new SimpleGrantedAuthority("ROLE_ADMIN");
    private static final HttpServer BACKEND;
    private static final AtomicReference<String> ULTIMA = new AtomicReference<>();
    private static final AtomicReference<String> ULTIMO_TOKEN = new AtomicReference<>();

    static {
        try {
            BACKEND = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        } catch (IOException ex) {
            throw new IllegalStateException(ex);
        }
        BACKEND.createContext("/", ex -> {
            String cuerpo = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            ULTIMA.set(ex.getRequestMethod() + " " + ex.getRequestURI() + " " + cuerpo);
            ULTIMO_TOKEN.set(ex.getRequestHeaders().getFirst("Authorization"));
            if (ex.getRequestURI().getPath().equals("/api/pagos/webpay/retorno")) {
                ex.getResponseHeaders().add("Location", "https://sitio/pago?resultado=ok");
                ex.sendResponseHeaders(302, -1);
            } else if (ex.getRequestMethod().equals("POST")) {
                ex.getResponseHeaders().add("Content-Type", "application/json");
                ex.getResponseHeaders().add("Location", "/api/productos/9");
                byte[] r = "{\"id\":9}".getBytes(StandardCharsets.UTF_8);
                ex.sendResponseHeaders(201, r.length);
                ex.getResponseBody().write(r);
            } else {
                ex.getResponseHeaders().add("Content-Type", "application/json");
                byte[] r = "[{\"id\":1}]".getBytes(StandardCharsets.UTF_8);
                ex.sendResponseHeaders(200, r.length);
                ex.getResponseBody().write(r);
            }
            ex.close();
        });
        BACKEND.start();
    }

    @DynamicPropertySource
    static void destinos(DynamicPropertyRegistry registry) {
        String url = "http://localhost:" + BACKEND.getAddress().getPort();
        registry.add("app.productos-url", () -> url);
        registry.add("app.pedidos-url", () -> url);
        registry.add("app.notificaciones-url", () -> url);
        registry.add("app.mensajeria-url", () -> "http://localhost:1"); // nadie escucha: simula una caída
    }

    @AfterAll
    static void detener() {
        BACKEND.stop(0);
    }

    @Autowired
    private MockMvc mvc;

    @BeforeEach
    void limpiar() {
        ULTIMA.set(null);
        ULTIMO_TOKEN.set(null);
    }

    @Test
    void reenviaAlMicroservicioConLaQuery() throws Exception {
        // El reenvío del header Authorization (token relay) se verifica contra AWS en infra/smoke-test.sh:
        // aquí el token lo simula jwt() y no viaja como header.
        mvc.perform(get("/api/productos").queryParam("categoria", "Pizzas").with(jwt().authorities(CLIENTE)))
                .andExpect(status().isOk())
                .andExpect(content().json("[{\"id\":1}]"));
        assertThat(ULTIMA.get()).startsWith("GET /api/productos?categoria=Pizzas");
    }

    @Test
    void adminCreaProductoYSeConservanStatusYLocation() throws Exception {
        mvc.perform(post("/api/productos").contentType(MediaType.APPLICATION_JSON).content("{\"nombre\":\"X\"}")
                        .with(jwt().authorities(ADMIN)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/productos/9"));
        assertThat(ULTIMA.get()).isEqualTo("POST /api/productos {\"nombre\":\"X\"}");
    }

    @Test
    void clienteNoPuedeAdministrarYNoSeReenvia() throws Exception {
        mvc.perform(post("/api/productos").contentType(MediaType.APPLICATION_JSON).content("{}")
                .with(jwt().authorities(CLIENTE))).andExpect(status().isForbidden());
        mvc.perform(get("/api/mensajeria/queues").with(jwt().authorities(CLIENTE))).andExpect(status().isForbidden());
        mvc.perform(get("/api/mensajes-fallidos").with(jwt().authorities(CLIENTE))).andExpect(status().isForbidden());
        assertThat(ULTIMA.get()).isNull();
    }

    @Test
    void sinTokenResponde401() throws Exception {
        mvc.perform(get("/api/pedidos")).andExpect(status().isUnauthorized());
        assertThat(ULTIMA.get()).isNull();
    }

    @Test
    void retornoDeWebpayEsPublicoYConservaLaRedireccion() throws Exception {
        mvc.perform(post("/api/pagos/webpay/retorno").contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .content("token_ws=t1"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "https://sitio/pago?resultado=ok"));
    }

    @Test
    void microservicioCaidoResponde502() throws Exception {
        mvc.perform(get("/api/mensajeria/queues").with(jwt().authorities(ADMIN))).andExpect(status().isBadGateway());
    }

    @Test
    void rutaSinMicroservicioResponde404() throws Exception {
        mvc.perform(get("/api/inexistente").with(jwt().authorities(ADMIN))).andExpect(status().isNotFound());
    }
}
