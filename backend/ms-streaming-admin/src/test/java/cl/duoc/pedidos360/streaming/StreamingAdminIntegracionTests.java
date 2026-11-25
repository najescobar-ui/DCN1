package cl.duoc.pedidos360.streaming;

import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.web.servlet.MockMvc;

/**
 * API del administrador contra un broker Kafka embebido. El factor de replicación 3 (y la caída de un broker) se
 * prueba contra el clúster real con infra/smoke-test.sh y infra/demo-kafka.sh.
 */
@SpringBootTest(properties = { "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}", "streaming.admin.timeout=15s" })
@AutoConfigureMockMvc
@EmbeddedKafka(partitions = 3, topics = "pedidos.actividad")
class StreamingAdminIntegracionTests {

    private static final SimpleGrantedAuthority SCOPE = new SimpleGrantedAuthority("SCOPE_pedidos360/streaming.admin");
    private static final SimpleGrantedAuthority ADMIN = new SimpleGrantedAuthority("ROLE_ADMIN");
    private static final String JSON = MediaType.APPLICATION_JSON_VALUE;

    @Autowired
    private MockMvc mvc;
    @Autowired
    private EmbeddedKafkaBroker broker;

    private static JwtRequestPostProcessor admin() {
        return jwt().authorities(SCOPE, ADMIN);
    }

    // ---------- Seguridad ----------

    @Test
    void sinTokenResponde401YSinScopeOSinRol403() throws Exception {
        mvc.perform(get("/api/streaming/topics")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/streaming/topics").with(jwt().authorities(ADMIN))).andExpect(status().isForbidden());
        mvc.perform(get("/api/streaming/topics").with(jwt().authorities(SCOPE, new SimpleGrantedAuthority("ROLE_CLIENTE"))))
                .andExpect(status().isForbidden());
    }

    // ---------- Clúster y tópicos ----------

    @Test
    void elClusterMuestraSusBrokersYElControlador() throws Exception {
        mvc.perform(get("/api/streaming/cluster").with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.brokers", hasSize(1)))
                .andExpect(jsonPath("$.controlador").exists());
    }

    @Test
    void cicloDeVidaDeUnTopico() throws Exception {
        mvc.perform(post("/api/streaming/topics").with(admin()).contentType(JSON).content("""
                {"nombre":"pruebas.ciclo","particiones":3,"replicacion":1,"retencionMs":3600000,"cleanupPolicy":"DELETE"}"""))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/streaming/topics/pruebas.ciclo"))
                .andExpect(jsonPath("$.particiones", hasSize(3)))
                .andExpect(jsonPath("$.particiones[*].replicas", everyItem(hasSize(1))))
                .andExpect(jsonPath("$.configuracion['retention.ms']").value("3600000"))
                .andExpect(jsonPath("$.configuracion['min.insync.replicas']").value("1"));

        mvc.perform(post("/api/streaming/topics").with(admin()).contentType(JSON)
                .content("{\"nombre\":\"pruebas.ciclo\",\"particiones\":3,\"replicacion\":1}"))
                .andExpect(status().isConflict());

        mvc.perform(patch("/api/streaming/topics/pruebas.ciclo").with(admin()).contentType(JSON)
                .content("{\"retencionMs\":7200000,\"cleanupPolicy\":\"COMPACT_DELETE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.configuracion['retention.ms']").value("7200000"))
                .andExpect(jsonPath("$.configuracion['cleanup.policy']").value("compact,delete"));

        mvc.perform(post("/api/streaming/topics/pruebas.ciclo/partitions").with(admin()).contentType(JSON)
                .content("{\"total\":6}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.particiones", hasSize(6)));
        mvc.perform(post("/api/streaming/topics/pruebas.ciclo/partitions").with(admin()).contentType(JSON)
                .content("{\"total\":4}"))
                .andExpect(status().isBadRequest());

        mvc.perform(delete("/api/streaming/topics/pruebas.ciclo").with(admin())).andExpect(status().isNoContent());
        mvc.perform(get("/api/streaming/topics/pruebas.ciclo").with(admin())).andExpect(status().isNotFound());
    }

    @Test
    void validacionDeLaEntrada() throws Exception {
        mvc.perform(post("/api/streaming/topics").with(admin()).contentType(JSON)
                .content("{\"nombre\":\"__interno\",\"particiones\":0,\"replicacion\":4}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores.nombre").exists())
                .andExpect(jsonPath("$.errores.particiones").exists())
                .andExpect(jsonPath("$.errores.replicacion").exists());
        mvc.perform(post("/api/streaming/topics").with(admin()).contentType(JSON)
                .content("{\"nombre\":\"pruebas.isr\",\"particiones\":1,\"replicacion\":2,\"minInsync\":3}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores.minInsyncCoherente").value("minInsync no puede ser mayor que replicacion"));
        mvc.perform(post("/api/streaming/topics").with(admin()).contentType(JSON)
                .content("{\"nombre\":\"pruebas.rf\",\"particiones\":1,\"replicacion\":3}"))
                .andExpect(status().isBadRequest()); // más réplicas que brokers: lo rechaza Kafka
        mvc.perform(patch("/api/streaming/topics/pedidos.actividad").with(admin()).contentType(JSON).content("{}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errores.algunCambio").exists());
    }

    @Test
    void losTopicosDelSistemaYLosInternosNoSeEliminan() throws Exception {
        mvc.perform(delete("/api/streaming/topics/pedidos.actividad").with(admin()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("El tópico pedidos.actividad es parte del sistema y no se puede eliminar"));
        mvc.perform(delete("/api/streaming/topics/__consumer_offsets").with(admin())).andExpect(status().isBadRequest());
    }

    // ---------- Demo y consumer groups ----------

    @Test
    void laDemoPublicaLosEventosDeUnPedidoEnUnaMismaParticion() throws Exception {
        mvc.perform(post("/api/streaming/events").with(admin()).contentType(JSON).content("{\"escenario\":\"VALIDO\"}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[1].particion").value(org.hamcrest.Matchers.anything()));
        mvc.perform(post("/api/streaming/events").with(admin()).contentType(JSON).content("{\"escenario\":\"RARO\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void lagDeUnGrupoYResetDeOffsetsSoloConElGrupoDetenido() throws Exception {
        mvc.perform(post("/api/streaming/events").with(admin()).contentType(JSON)
                .content("{\"escenario\":\"LOTE\",\"cantidad\":5}")).andExpect(status().isAccepted());
        Map<String, Object> props = KafkaTestUtils.consumerProps(broker, "grupo-prueba", false);
        try (Consumer<String, String> c = new DefaultKafkaConsumerFactory<>(props, new StringDeserializer(),
                new StringDeserializer()).createConsumer()) {
            c.subscribe(List.of("pedidos.actividad"));
            KafkaTestUtils.getRecords(c, Duration.ofSeconds(5), 15);
            c.commitSync();
            mvc.perform(get("/api/streaming/groups/grupo-prueba").with(admin()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.miembros", hasSize(1)))
                    .andExpect(jsonPath("$.particiones", hasSize(3)))
                    .andExpect(jsonPath("$.lagTotal").value(0));
            mvc.perform(post("/api/streaming/groups/grupo-prueba/offsets").with(admin()).contentType(JSON)
                    .content("{\"topico\":\"pedidos.actividad\",\"destino\":\"INICIO\"}"))
                    .andExpect(status().isConflict());
        }
        // Con el consumidor cerrado el grupo queda vacío y sí se puede rebobinar: el lag pasa a ser todo lo retenido.
        org.awaitility.Awaitility.await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                mvc.perform(post("/api/streaming/groups/grupo-prueba/offsets").with(admin()).contentType(JSON)
                        .content("{\"topico\":\"pedidos.actividad\",\"destino\":\"INICIO\"}"))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.particiones[*].despues", everyItem(is(0)))));
        mvc.perform(get("/api/streaming/groups").with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id=='grupo-prueba')].lagTotal").value(org.hamcrest.Matchers.hasItem(
                        org.hamcrest.Matchers.greaterThanOrEqualTo(15))));
        mvc.perform(post("/api/streaming/groups/grupo-prueba/offsets").with(admin()).contentType(JSON)
                .content("{\"topico\":\"pedidos.actividad\",\"destino\":\"FECHA\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errores.fechaCoherente").exists());
        mvc.perform(get("/api/streaming/groups/no-existe").with(admin())).andExpect(status().isNotFound());
    }
}
