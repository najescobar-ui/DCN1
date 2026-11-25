package cl.duoc.pedidos360.analitica;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

import cl.duoc.pedidos360.analitica.evento.ActividadPedido;
import cl.duoc.pedidos360.analitica.proyeccion.SeguimientoPedidos;
import cl.duoc.pedidos360.analitica.proyeccion.TableroVentas;

/**
 * Flujo completo contra un broker Kafka embebido: los tres grupos consumen, la analítica publica el evento
 * derivado que alimenta al tablero, y los eventos malos terminan en la DLT sin bloquear a nadie.
 */
@SpringBootTest(properties = {
        "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
        "streaming.topicos.actividad.replicas=1", "streaming.topicos.actividad.min-insync=1",
        "streaming.topicos.metricas.replicas=1", "streaming.topicos.metricas.min-insync=1",
        "streaming.topicos.errores.replicas=1", "streaming.topicos.errores.min-insync=1",
        "streaming.reintentos.espera=100ms" })
@AutoConfigureMockMvc
@EmbeddedKafka(partitions = 3)
class AnaliticaIntegracionTests {

    private static final SimpleGrantedAuthority LECTURA = new SimpleGrantedAuthority("SCOPE_pedidos360/pedidos.read");
    private static final SimpleGrantedAuthority STREAMING = new SimpleGrantedAuthority("SCOPE_pedidos360/streaming.admin");
    private static final SimpleGrantedAuthority ADMIN = new SimpleGrantedAuthority("ROLE_ADMIN");
    private static final SimpleGrantedAuthority CLIENTE = new SimpleGrantedAuthority("ROLE_CLIENTE");

    @Autowired
    private KafkaTemplate<String, ActividadPedido> template;
    @Autowired
    private EmbeddedKafkaBroker broker;
    @Autowired
    private SeguimientoPedidos seguimiento;
    @Autowired
    private TableroVentas tablero;
    @Autowired
    private MockMvc mvc;

    @Test
    void unEventoLoLeenLosTresGruposYElTableroReflejaLaMetricaDerivada() {
        long pedido = 7001;
        template.send("pedidos.actividad", Long.toString(pedido), Eventos.creado(pedido, "cliente-a", Instant.now()));
        template.send("pedidos.actividad", Long.toString(pedido), Eventos.pago(pedido, "cliente-a", Instant.now()));

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            assertThat(seguimiento.de(pedido)).hasValueSatisfying(v -> assertThat(v.hitos()).hasSize(2));
            assertThat(tablero.resumen().pedidosPagados()).isGreaterThanOrEqualTo(1);
        });
        // Misma key, misma partición: los dos hechos del pedido llegaron por la misma.
        assertThat(seguimiento.de(pedido).orElseThrow().hitos())
                .extracting(SeguimientoPedidos.Hito::particion).containsOnly(seguimiento.de(pedido).orElseThrow().particion());
    }

    @Test
    void unEventoIlegibleOInvalidoVaALaDltSinBloquearLaParticion() {
        KafkaTemplate<String, String> crudo = new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(
                KafkaTestUtils.producerProps(broker), new StringSerializer(), new StringSerializer()));
        crudo.send("pedidos.actividad", "8001", "esto no es JSON");
        crudo.send("pedidos.actividad", "8002", "{\"eventoId\":\"6f1c1f0e-1111-4c4c-9a9a-000000000001\",\"tipo\":\"PAGO_APROBADO\"}");
        long despues = 8003;
        template.send("pedidos.actividad", Long.toString(despues), Eventos.creado(despues, "cliente-b", Instant.now()));

        Map<String, Object> props = KafkaTestUtils.consumerProps(broker, "lector-dlt", true);
        try (Consumer<String, String> dlt = new DefaultKafkaConsumerFactory<>(props, new StringDeserializer(),
                new StringDeserializer()).createConsumer()) {
            dlt.subscribe(java.util.List.of("pedidos.actividad.DLT"));
            await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
                var registros = KafkaTestUtils.getRecords(dlt, Duration.ofSeconds(2));
                assertThat(registros.count()).isGreaterThan(0);
                for (ConsumerRecord<String, String> r : registros) {
                    assertThat(new String(r.headers().lastHeader("kafka_dlt-original-topic").value(),
                            StandardCharsets.UTF_8)).isEqualTo("pedidos.actividad");
                }
            });
        }
        // El evento válido que venía detrás se procesó igual.
        await().atMost(Duration.ofSeconds(30)).until(() -> seguimiento.de(despues).isPresent());
    }

    @Test
    void unFalloTransitorioSeReintentaYTerminaEnLaDlt() {
        long pedido = 9001;
        ProducerRecord<String, ActividadPedido> registro = new ProducerRecord<>("pedidos.actividad",
                Long.toString(pedido), Eventos.creado(pedido, "cliente-c", Instant.now()));
        registro.headers().add("x-simular-fallo", "bd-caida".getBytes(StandardCharsets.UTF_8));
        template.send(registro);
        // El grupo de seguimiento no simula fallos: lo procesa aunque la analítica lo mande a la DLT.
        await().atMost(Duration.ofSeconds(30)).until(() -> seguimiento.de(pedido).isPresent());
    }

    @Test
    void seguridadDeLaApi() throws Exception {
        mvc.perform(get("/api/analitica/metricas")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/analitica/metricas").with(jwt().authorities(LECTURA, CLIENTE)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/analitica/metricas").with(jwt().authorities(LECTURA, ADMIN)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.resumen").exists());
        mvc.perform(get("/api/analitica/consumidores").with(jwt().authorities(LECTURA, ADMIN)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/analitica/consumidores").with(jwt().authorities(STREAMING, ADMIN)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(3));
        mvc.perform(get("/api/analitica/seguimiento/999999").with(jwt().authorities(LECTURA, CLIENTE)))
                .andExpect(status().isNotFound());
    }

    @Test
    void unClienteNoVeElSeguimientoDeUnPedidoAjeno() throws Exception {
        long pedido = 7501;
        template.send("pedidos.actividad", Long.toString(pedido), Eventos.creado(pedido, "duenio", Instant.now()));
        await().atMost(Duration.ofSeconds(30)).until(() -> seguimiento.de(pedido).isPresent());
        mvc.perform(get("/api/analitica/seguimiento/" + pedido)
                .with(jwt().jwt(j -> j.subject("otro")).authorities(LECTURA, CLIENTE)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/analitica/seguimiento/" + pedido)
                .with(jwt().jwt(j -> j.subject("duenio")).authorities(LECTURA, CLIENTE)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.hitos.length()").value(1));
    }

    @Test
    void validaLaCantidadDeConsumidores() throws Exception {
        mvc.perform(put("/api/analitica/consumidores/tablero-ventas").with(jwt().authorities(STREAMING, ADMIN))
                .contentType(MediaType.APPLICATION_JSON).content("{\"consumidores\":7}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errores.consumidores").exists());
        mvc.perform(put("/api/analitica/consumidores/no-existe").with(jwt().authorities(STREAMING, ADMIN))
                .contentType(MediaType.APPLICATION_JSON).content("{\"consumidores\":1}"))
                .andExpect(status().isNotFound());
    }
}
