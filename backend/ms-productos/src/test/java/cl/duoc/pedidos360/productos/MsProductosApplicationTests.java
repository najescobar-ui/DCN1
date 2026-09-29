package cl.duoc.pedidos360.productos;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@SpringBootTest
@AutoConfigureMockMvc
class MsProductosApplicationTests {

    private static final SimpleGrantedAuthority READ = new SimpleGrantedAuthority("SCOPE_pedidos360/productos.read");
    private static final SimpleGrantedAuthority WRITE = new SimpleGrantedAuthority("SCOPE_pedidos360/productos.write");
    private static final SimpleGrantedAuthority ADMIN = new SimpleGrantedAuthority("ROLE_ADMIN");
    private static final SimpleGrantedAuthority CLIENTE = new SimpleGrantedAuthority("ROLE_CLIENTE");

    private static final String NUEVO = """
            {"nombre":"Empanada de pino","descripcion":"Horneada","categoria":"Empanadas","precio":2500,"stock":30,
             "imagenUrl":"/img/productos/empanada.jpg"}
            """;

    @Autowired
    private MockMvc mvc;

    @Test
    void sinTokenResponde401() throws Exception {
        mvc.perform(get("/api/productos"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().exists("WWW-Authenticate"));
    }

    @Test
    void healthEsPublico() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }

    @Test
    void conScopeDeLecturaListaElCatalogo() throws Exception {
        mvc.perform(get("/api/productos").with(jwt().authorities(READ, CLIENTE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].nombre").exists());
    }

    @Test
    void sinScopeDeLecturaResponde403() throws Exception {
        mvc.perform(get("/api/productos").with(jwt().authorities(CLIENTE)))
                .andExpect(status().isForbidden());
    }

    @Test
    void clienteNoPuedeCrearProductos() throws Exception {
        mvc.perform(json(post("/api/productos"), NUEVO).with(jwt().authorities(WRITE, CLIENTE)))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCreaProducto() throws Exception {
        mvc.perform(json(post("/api/productos"), NUEVO).with(jwt().authorities(WRITE, ADMIN)))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.nombre").value("Empanada de pino"))
                .andExpect(jsonPath("$.categoria").value("Empanadas"));
    }

    @Test
    void productoInvalidoResponde400() throws Exception {
        mvc.perform(json(post("/api/productos"), "{\"nombre\":\"\",\"precio\":-1}").with(jwt().authorities(WRITE, ADMIN)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void precioAnteriorMenorAlPrecioResponde400() throws Exception {
        String body = "{\"nombre\":\"X\",\"categoria\":\"Pizzas\",\"precio\":5000,\"precioAnterior\":4000,\"stock\":1}";
        mvc.perform(json(post("/api/productos"), body).with(jwt().authorities(WRITE, ADMIN)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void catalogoIncluyeCategoriaEImagen() throws Exception {
        mvc.perform(get("/api/productos/1").with(jwt().authorities(READ)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.categoria").exists())
                .andExpect(jsonPath("$.imagenUrl").value(org.hamcrest.Matchers.startsWith("/img/productos/")));
    }

    @Test
    void productoInexistenteResponde404() throws Exception {
        mvc.perform(get("/api/productos/99999").with(jwt().authorities(READ)))
                .andExpect(status().isNotFound());
    }

    @Test
    void adminEliminaProducto() throws Exception {
        mvc.perform(delete("/api/productos/5").with(jwt().authorities(WRITE, ADMIN)))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/productos/5").with(jwt().authorities(READ)))
                .andExpect(status().isNotFound());
    }

    private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request, String body) {
        return request.contentType(MediaType.APPLICATION_JSON).content(body);
    }
}
