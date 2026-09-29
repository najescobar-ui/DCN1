package cl.duoc.pedidos360.pedidos.pago;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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
import cl.duoc.pedidos360.pedidos.model.EstadoPago;
import cl.duoc.pedidos360.pedidos.repository.PedidoRepository;

@SpringBootTest(properties = "app.pagos.frontend-origins=https://front.test,http://localhost:4200")
@AutoConfigureMockMvc
class PagoWebpayTests {

    private static final String TOKEN = "01ab-token-de-prueba";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private PedidoRepository repository;

    @MockitoBean
    private ProductosClient productosClient;

    @MockitoBean
    private WebpayClient webpay;

    @BeforeEach
    void setUp() {
        repository.deleteAll();
        given(productosClient.obtener(1L)).willReturn(new ProductoDto(1L, "Pizza", new BigDecimal("12990"), 10));
        given(webpay.crear(anyString(), anyString(), anyLong()))
                .willReturn(new WebpayClient.Transaccion(TOKEN, "https://webpay3gint.transbank.cl/webpayserver/initTransaction"));
    }

    @Test
    void iniciaPagoConTarjetaYDevuelveUrlDeWebpay() throws Exception {
        Long id = crearPedido("TARJETA");
        mvc.perform(post("/api/pedidos/" + id + "/pago/webpay").contentType(MediaType.APPLICATION_JSON)
                .content("{\"origen\":\"https://front.test\"}").with(cliente()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value(TOKEN))
                .andExpect(jsonPath("$.url").value(containsString("webpay")));
    }

    @Test
    void noSePuedePagarConWebpayUnPedidoEnEfectivo() throws Exception {
        Long id = crearPedido("EFECTIVO");
        mvc.perform(post("/api/pedidos/" + id + "/pago/webpay").with(cliente())).andExpect(status().isConflict());
    }

    @Test
    void retornoAprobadoMarcaPagadoYVuelveAlFrontend() throws Exception {
        Long id = crearPedido("TARJETA");
        iniciar(id);
        given(webpay.confirmar(TOKEN)).willReturn(new WebpayClient.Resultado("AUTHORIZED", 0, "1213",
                "P360-" + id + "-x", 25980L, new WebpayClient.CardDetail("6623")));

        // Public route: Webpay sends the browser without a bearer token.
        mvc.perform(post("/api/pagos/webpay/retorno").param("token_ws", TOKEN))
                .andExpect(status().isSeeOther())
                .andExpect(header().string("Location", "https://front.test/pedidos?pago=aprobado&pedido=" + id));

        var pedido = repository.findById(id).orElseThrow();
        org.assertj.core.api.Assertions.assertThat(pedido.getEstadoPago()).isEqualTo(EstadoPago.PAGADO);
        org.assertj.core.api.Assertions.assertThat(pedido.getPagoTarjeta()).isEqualTo("6623");
    }

    @Test
    void retornoConMontoDistintoSeRechaza() throws Exception {
        Long id = crearPedido("TARJETA");
        iniciar(id);
        given(webpay.confirmar(TOKEN)).willReturn(new WebpayClient.Resultado("AUTHORIZED", 0, "1213",
                "P360-" + id + "-x", 1L, null));
        mvc.perform(get("/api/pagos/webpay/retorno").param("token_ws", TOKEN))
                .andExpect(header().string("Location", containsString("pago=rechazado")));
    }

    @Test
    void retornoCanceladoPorElClienteQuedaAnulado() throws Exception {
        Long id = crearPedido("TARJETA");
        iniciar(id);
        mvc.perform(post("/api/pagos/webpay/retorno").param("TBK_TOKEN", TOKEN).param("TBK_ORDEN_COMPRA", "P360-" + id + "-x"))
                .andExpect(status().isSeeOther())
                .andExpect(header().string("Location", containsString("pago=anulado")));
        org.assertj.core.api.Assertions.assertThat(repository.findById(id).orElseThrow().getEstadoPago())
                .isEqualTo(EstadoPago.RECHAZADO);
    }

    @Test
    void retornoConTokenDesconocidoVuelveConError() throws Exception {
        mvc.perform(get("/api/pagos/webpay/retorno").param("token_ws", "no-existe"))
                .andExpect(header().string("Location", "https://front.test/pedidos?pago=error"));
    }

    private void iniciar(Long id) throws Exception {
        mvc.perform(post("/api/pedidos/" + id + "/pago/webpay").contentType(MediaType.APPLICATION_JSON)
                .content("{\"origen\":\"https://front.test\"}").with(cliente())).andExpect(status().isOk());
    }

    private Long crearPedido(String metodo) throws Exception {
        String body = """
                {"items":[{"productoId":1,"cantidad":2}],"direccionEntrega":"Av. Siempre Viva 742",
                 "telefono":"912345678","metodoPago":"%s"}
                """.formatted(metodo);
        String json = mvc.perform(post("/api/pedidos").contentType(MediaType.APPLICATION_JSON).content(body).with(cliente()))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return Long.valueOf(json.replaceAll("^\\{\"id\":(\\d+).*", "$1"));
    }

    private static JwtRequestPostProcessor cliente() {
        return jwt().jwt(j -> j.subject("ana").claim("username", "ana")).authorities(
                new SimpleGrantedAuthority("SCOPE_pedidos360/pedidos.read"),
                new SimpleGrantedAuthority("SCOPE_pedidos360/pedidos.write"),
                new SimpleGrantedAuthority("ROLE_CLIENTE"));
    }
}
