package cl.duoc.pedidos360.bff;

import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import cl.duoc.pedidos360.bff.client.BackendClient;
import cl.duoc.pedidos360.bff.dto.PedidoDto;
import cl.duoc.pedidos360.bff.dto.ProductoDto;

@SpringBootTest
@AutoConfigureMockMvc
class BffApplicationTests {

    private static final SimpleGrantedAuthority READ = new SimpleGrantedAuthority("SCOPE_pedidos360/pedidos.read");
    private static final SimpleGrantedAuthority CLIENTE = new SimpleGrantedAuthority("ROLE_CLIENTE");

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private BackendClient backend;

    @Test
    void sinTokenResponde401() throws Exception {
        mvc.perform(get("/api/bff/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void meExponeRolesYScopesDelToken() throws Exception {
        mvc.perform(get("/api/bff/me").with(jwt().jwt(j -> j.subject("abc").claim("username", "ana")
                        .claim("client_id", "app-123")).authorities(READ, CLIENTE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("ana"))
                .andExpect(jsonPath("$.clientId").value("app-123"))
                .andExpect(jsonPath("$.roles[0]").value("CLIENTE"))
                .andExpect(jsonPath("$.scopes[0]").value("pedidos360/pedidos.read"));
    }

    @Test
    void resumenAgregaAmbosMicroservicios() throws Exception {
        given(backend.productos()).willReturn(List.of(new ProductoDto(1L, "Pizza", new BigDecimal("12990"), 5)));
        given(backend.pedidos()).willReturn(List.of(
                new PedidoDto(1L, "ana", "PENDIENTE", new BigDecimal("1000"), Instant.now()),
                new PedidoDto(2L, "ana", "CANCELADO", new BigDecimal("500"), Instant.now())));
        mvc.perform(get("/api/bff/resumen").with(jwt().authorities(READ, CLIENTE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.productosDisponibles").value(1))
                .andExpect(jsonPath("$.totalPedidos").value(2))
                .andExpect(jsonPath("$.montoTotal").value(1000))
                .andExpect(jsonPath("$.pedidosPorEstado.CANCELADO").value(1));
    }

    @Test
    void resumenSinScopeResponde403() throws Exception {
        mvc.perform(get("/api/bff/resumen").with(jwt().authorities(CLIENTE))).andExpect(status().isForbidden());
    }
}
