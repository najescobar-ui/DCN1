package cl.duoc.pedidos360.pedidos;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import cl.duoc.pedidos360.pedidos.client.ProductoDto;
import cl.duoc.pedidos360.pedidos.client.ProductosClient;
import cl.duoc.pedidos360.pedidos.exception.ProductoNoDisponibleException;
import cl.duoc.pedidos360.pedidos.repository.PedidoRepository;

@SpringBootTest
@AutoConfigureMockMvc
class MsPedidosApplicationTests {

    private static final SimpleGrantedAuthority READ = new SimpleGrantedAuthority("SCOPE_pedidos360/pedidos.read");
    private static final SimpleGrantedAuthority WRITE = new SimpleGrantedAuthority("SCOPE_pedidos360/pedidos.write");

    private static final String PEDIDO = """
            {"items":[{"productoId":1,"cantidad":2}],"direccionEntrega":"Av. Siempre Viva 742",
             "telefono":"+56 9 1234 5678","latitud":-33.43,"longitud":-70.62,"metodoPago":"TARJETA"}
            """;

    @Autowired
    private MockMvc mvc;

    @Autowired
    private PedidoRepository repository;

    @MockitoBean
    private ProductosClient productosClient;

    @BeforeEach
    void setUp() {
        repository.deleteAll();
        given(productosClient.obtener(1L)).willReturn(new ProductoDto(1L, "Pizza", new BigDecimal("12990"), 10));
    }

    @Test
    void sinTokenResponde401() throws Exception {
        mvc.perform(get("/api/pedidos")).andExpect(status().isUnauthorized());
    }

    @Test
    void clienteCreaPedidoConPrecioDelCatalogo() throws Exception {
        mvc.perform(post("/api/pedidos").contentType(MediaType.APPLICATION_JSON).content(PEDIDO).with(cliente("ana")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.estado").value("PENDIENTE"))
                .andExpect(jsonPath("$.total").value(25980))
                .andExpect(jsonPath("$.clienteId").value("ana"))
                .andExpect(jsonPath("$.telefonoContacto").value("+56912345678"))
                .andExpect(jsonPath("$.metodoPago").value("TARJETA"))
                .andExpect(jsonPath("$.estadoPago").value("PENDIENTE"));
    }

    @Test
    void clienteSoloVeSusPedidosYAdminVeTodos() throws Exception {
        crearPedido("ana");
        crearPedido("pedro");
        mvc.perform(get("/api/pedidos").with(cliente("ana")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
        mvc.perform(get("/api/pedidos").with(admin()))
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void clienteNoPuedeVerPedidoAjeno() throws Exception {
        Long id = crearPedido("pedro");
        mvc.perform(get("/api/pedidos/" + id).with(cliente("ana"))).andExpect(status().isForbidden());
    }

    @Test
    void soloAdminCambiaEstado() throws Exception {
        Long id = crearPedido("ana");
        String body = "{\"estado\":\"CONFIRMADO\"}";
        mvc.perform(patch("/api/pedidos/" + id + "/estado").contentType(MediaType.APPLICATION_JSON).content(body)
                .with(cliente("ana"))).andExpect(status().isForbidden());
        mvc.perform(patch("/api/pedidos/" + id + "/estado").contentType(MediaType.APPLICATION_JSON).content(body)
                .with(admin())).andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("CONFIRMADO"));
    }

    @Test
    void elPedidoAvanzaEnOrdenHastaEntregado() throws Exception {
        Long id = crearPedido("ana");
        for (String estado : new String[] { "CONFIRMADO", "DESPACHADO", "ENTREGADO" }) {
            cambiarEstado(id, estado).andExpect(status().isOk()).andExpect(jsonPath("$.estado").value(estado));
        }
    }

    @Test
    void alEntregarUnPedidoEnEfectivoElPagoQuedaConfirmado() throws Exception {
        String body = PEDIDO.replace("\"TARJETA\"", "\"EFECTIVO\"");
        String json = mvc.perform(post("/api/pedidos").contentType(MediaType.APPLICATION_JSON).content(body)
                .with(cliente("ana"))).andReturn().getResponse().getContentAsString();
        Long id = Long.valueOf(json.replaceAll("^\\{\"id\":(\\d+).*", "$1"));
        cambiarEstado(id, "CONFIRMADO");
        cambiarEstado(id, "DESPACHADO").andExpect(jsonPath("$.estadoPago").value("PENDIENTE"));
        cambiarEstado(id, "ENTREGADO").andExpect(jsonPath("$.estadoPago").value("PAGADO"));
    }

    @Test
    void noSePuedeSaltarPasosNiRetroceder() throws Exception {
        Long id = crearPedido("ana");
        cambiarEstado(id, "ENTREGADO").andExpect(status().isConflict());
        cambiarEstado(id, "CONFIRMADO").andExpect(status().isOk());
        cambiarEstado(id, "PENDIENTE").andExpect(status().isConflict());
    }

    @Test
    void noSePuedeCancelarDespuesDeDespachar() throws Exception {
        Long id = crearPedido("ana");
        cambiarEstado(id, "CONFIRMADO");
        cambiarEstado(id, "DESPACHADO");
        cambiarEstado(id, "CANCELADO").andExpect(status().isConflict());
    }

    @Test
    void elClienteNoPuedeCancelarUnPedidoPagado() throws Exception {
        Long id = crearPedido("ana");
        mvc.perform(patch("/api/pedidos/" + id + "/pago").contentType(MediaType.APPLICATION_JSON)
                .content("{\"estadoPago\":\"PAGADO\"}").with(admin())).andExpect(status().isOk());
        mvc.perform(post("/api/pedidos/" + id + "/cancelar").with(cliente("ana")))
                .andExpect(status().isConflict());
    }

    private org.springframework.test.web.servlet.ResultActions cambiarEstado(Long id, String estado) throws Exception {
        return mvc.perform(patch("/api/pedidos/" + id + "/estado").contentType(MediaType.APPLICATION_JSON)
                .content("{\"estado\":\"" + estado + "\"}").with(admin()));
    }

    @Test
    void cancelarSoloSiEstaPendiente() throws Exception {
        Long id = crearPedido("ana");
        mvc.perform(post("/api/pedidos/" + id + "/cancelar").with(cliente("ana")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("CANCELADO"));
        mvc.perform(post("/api/pedidos/" + id + "/cancelar").with(cliente("ana")))
                .andExpect(status().isConflict());
    }

    @Test
    void productoInexistenteResponde422() throws Exception {
        given(productosClient.obtener(anyLong())).willThrow(new ProductoNoDisponibleException("Producto 9 no existe"));
        mvc.perform(post("/api/pedidos").contentType(MediaType.APPLICATION_JSON).content(PEDIDO).with(cliente("ana")))
                .andExpect(status().isUnprocessableContent());
    }

    @Test
    void telefonoInvalidoResponde400() throws Exception {
        String body = PEDIDO.replace("+56 9 1234 5678", "12345");
        mvc.perform(post("/api/pedidos").contentType(MediaType.APPLICATION_JSON).content(body).with(cliente("ana")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void pedidoSinMetodoDePagoResponde400() throws Exception {
        String body = PEDIDO.replace(",\"metodoPago\":\"TARJETA\"", "");
        mvc.perform(post("/api/pedidos").contentType(MediaType.APPLICATION_JSON).content(body).with(cliente("ana")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void soloAdminConfirmaPagosManuales() throws Exception {
        Long id = crearPedido("ana");
        String body = "{\"estadoPago\":\"PAGADO\"}";
        mvc.perform(patch("/api/pedidos/" + id + "/pago").contentType(MediaType.APPLICATION_JSON).content(body)
                .with(cliente("ana"))).andExpect(status().isForbidden());
        mvc.perform(patch("/api/pedidos/" + id + "/pago").contentType(MediaType.APPLICATION_JSON).content(body)
                .with(admin())).andExpect(status().isOk()).andExpect(jsonPath("$.estadoPago").value("PAGADO"));
    }

    @Test
    void pedidoSinItemsResponde400() throws Exception {
        mvc.perform(post("/api/pedidos").contentType(MediaType.APPLICATION_JSON).content("{\"items\":[]}")
                .with(cliente("ana"))).andExpect(status().isBadRequest());
    }

    private Long crearPedido(String sub) throws Exception {
        String json = mvc.perform(post("/api/pedidos").contentType(MediaType.APPLICATION_JSON).content(PEDIDO)
                .with(cliente(sub))).andReturn().getResponse().getContentAsString();
        return Long.valueOf(json.replaceAll("^\\{\"id\":(\\d+).*", "$1"));
    }

    private static JwtRequestPostProcessor cliente(String sub) {
        return jwt().jwt(j -> j.subject(sub).claim("username", sub))
                .authorities(READ, WRITE, new SimpleGrantedAuthority("ROLE_CLIENTE"));
    }

    private static JwtRequestPostProcessor admin() {
        return jwt().jwt(j -> j.subject("admin").claim("username", "admin"))
                .authorities(READ, WRITE, new SimpleGrantedAuthority("ROLE_ADMIN"));
    }
}
