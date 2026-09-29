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
            {"nombre":"Empanada de pino","descripcion":"Horneada","precio":2500,"stock":30}
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
                .andExpect(jsonPath("$.nombre").value("Empanada de pino"));
    }

    @Test
    void productoInvalidoResponde400() throws Exception {
        mvc.perform(json(post("/api/productos"), "{\"nombre\":\"\",\"precio\":-1}").with(jwt().authorities(WRITE, ADMIN)))
                .andExpect(status().isBadRequest());
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
