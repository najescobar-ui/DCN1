package cl.duoc.pedidos360.mensajeria;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import cl.duoc.pedidos360.mensajeria.config.AdminProperties;
import cl.duoc.pedidos360.mensajeria.dto.CrearColaRequest;
import cl.duoc.pedidos360.mensajeria.dto.Respuestas.ColaInfo;
import cl.duoc.pedidos360.mensajeria.exception.OperacionRechazadaException;
import cl.duoc.pedidos360.mensajeria.service.RabbitAdminService;

/** API del administrador: seguridad, validación de entrada y códigos HTTP. El servicio va simulado. */
@SpringBootTest
@AutoConfigureMockMvc
class MsMensajeriaAdminApplicationTests {

    private static final SimpleGrantedAuthority SCOPE = new SimpleGrantedAuthority("SCOPE_pedidos360/mensajeria.admin");
    private static final SimpleGrantedAuthority ADMIN = new SimpleGrantedAuthority("ROLE_ADMIN");

    @Autowired
    private MockMvc mvc;

    @Autowired
    private AdminProperties props;

    @MockitoBean
    private RabbitAdminService service;

    private static JwtRequestPostProcessor admin() {
        return jwt().jwt(j -> j.subject("admin")).authorities(SCOPE, ADMIN);
    }

    private static ColaInfo cola(String nombre) {
        return new ColaInfo(nombre, "quorum", true, "rabbit@rabbit1", 0, 0, 0, 0, Map.of());
    }

    // ---------- Seguridad ----------

    @Test
    void sinTokenResponde401() throws Exception {
        mvc.perform(get("/api/mensajeria/queues")).andExpect(status().isUnauthorized());
    }

    @Test
    void clienteConScopePeroSinRolAdminResponde403() throws Exception {
        mvc.perform(get("/api/mensajeria/queues")
                .with(jwt().authorities(SCOPE, new SimpleGrantedAuthority("ROLE_CLIENTE"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminSinScopeResponde403() throws Exception {
        mvc.perform(get("/api/mensajeria/queues").with(jwt().authorities(ADMIN))).andExpect(status().isForbidden());
    }

    @Test
    void adminConScopeListaColas() throws Exception {
        given(service.listarColas()).willReturn(List.of(cola("notificaciones.pedidos")));
        mvc.perform(get("/api/mensajeria/queues").with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].nombre").value("notificaciones.pedidos"));
    }

    // ---------- Validación ----------

    @Test
    void creaColaValidaResponde201ConLocation() throws Exception {
        given(service.crearCola(any())).willReturn(cola("pruebas.demo"));
        mvc.perform(post("/api/mensajeria/queues").with(admin()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"nombre\":\"pruebas.demo\",\"tipo\":\"QUORUM\",\"conDlq\":true}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/mensajeria/queues/pruebas.demo"));
    }

    @Test
    void nombreVacioResponde400ConDetallePorCampo() throws Exception {
        mvc.perform(post("/api/mensajeria/queues").with(admin()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"nombre\":\"\",\"tipo\":\"QUORUM\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores.nombre").exists());
        verify(service, never()).crearCola(any(CrearColaRequest.class));
    }

    @Test
    void nombreConEspaciosOCaracteresRarosResponde400() throws Exception {
        mvc.perform(post("/api/mensajeria/queues").with(admin()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"nombre\":\"mi cola/!\",\"tipo\":\"CLASSIC\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores.nombre").exists());
    }

    @Test
    void sinTipoResponde400() throws Exception {
        mvc.perform(post("/api/mensajeria/queues").with(admin()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"nombre\":\"pruebas.demo\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores.tipo").exists());
    }

    @Test
    void limiteDeEntregasEnColaClasicaEsConfiguracionInvalida() throws Exception {
        mvc.perform(post("/api/mensajeria/queues").with(admin()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"nombre\":\"pruebas.demo\",\"tipo\":\"CLASSIC\",\"limiteEntregas\":3}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores.limiteEntregasCoherente").value("limiteEntregas solo aplica a colas QUORUM"));
    }

    @Test
    void ttlNegativoResponde400() throws Exception {
        mvc.perform(post("/api/mensajeria/queues").with(admin()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"nombre\":\"pruebas.demo\",\"tipo\":\"QUORUM\",\"ttlMs\":-5}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores.ttlMs").exists());
    }

    @Test
    void tipoDeExchangeDesconocidoResponde400() throws Exception {
        mvc.perform(post("/api/mensajeria/exchanges").with(admin()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"nombre\":\"pruebas.ex\",\"tipo\":\"RARO\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void routingKeyInvalidaEnBindingResponde400() throws Exception {
        mvc.perform(post("/api/mensajeria/bindings").with(admin()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"exchange\":\"pedidos.eventos\",\"cola\":\"pruebas.demo\",\"routingKey\":\"con espacio\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores.routingKey").exists());
    }

    @Test
    void nombreInvalidoEnLaRutaResponde400() throws Exception {
        mvc.perform(delete("/api/mensajeria/queues/a").with(admin())).andExpect(status().isBadRequest());
    }

    // ---------- Reglas del servicio ----------

    @Test
    void eliminarColaDelSistemaResponde409() throws Exception {
        willThrow(new OperacionRechazadaException("es parte del sistema")).given(service)
                .eliminarCola("notificaciones.pedidos", true);
        mvc.perform(delete("/api/mensajeria/queues/notificaciones.pedidos").with(admin()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("es parte del sistema"));
    }

    @Test
    void eliminarColaPropiaResponde204() throws Exception {
        mvc.perform(delete("/api/mensajeria/queues/pruebas.demo").with(admin())).andExpect(status().isNoContent());
        verify(service).eliminarCola("pruebas.demo", true);
    }

    @Test
    void laTopologiaDelSistemaEstaProtegida() {
        org.assertj.core.api.Assertions.assertThat(props.esProtegido("pedidos.eventos")).isTrue();
        org.assertj.core.api.Assertions.assertThat(props.esProtegido("auditoria.eventos.dlq")).isTrue();
        org.assertj.core.api.Assertions.assertThat(props.esProtegido("amq.topic")).isTrue();
        org.assertj.core.api.Assertions.assertThat(props.esProtegido("pruebas.demo")).isFalse();
    }
}
