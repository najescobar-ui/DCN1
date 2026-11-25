package cl.duoc.pedidos360.streaming.service;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import cl.duoc.pedidos360.streaming.config.StreamingAdminProperties;
import cl.duoc.pedidos360.streaming.dto.PublicarEventosRequest;
import cl.duoc.pedidos360.streaming.dto.Respuestas.Publicado;
import cl.duoc.pedidos360.streaming.exception.ClusterNoDisponibleException;
import tools.jackson.databind.json.JsonMapper;

/**
 * Publica eventos de prueba en pedidos.actividad para mostrar en vivo cada comportamiento de los consumidores.
 * Los pedidos de la demo usan ids 900000 en adelante y el cliente "demo", para no confundirse con los reales.
 */
@Service
public class EventosDemoService {

    private static final Logger log = LoggerFactory.getLogger(EventosDemoService.class);
    private static final String[][] PRODUCTOS = { { "1", "Hamburguesa Classic Smash", "7990" },
            { "2", "Pizza Napolitana", "11990" }, { "3", "Sushi Platter", "14990" }, { "4", "Gyozas", "5490" },
            { "5", "Brownie", "3490" }, { "6", "Bebida lata", "1500" } };
    private static final String[] PAGOS = { "EFECTIVO", "TARJETA", "WEBPAY" };

    private final KafkaTemplate<String, String> template;
    private final StreamingAdminProperties props;
    private final JsonMapper json;

    public EventosDemoService(KafkaTemplate<String, String> template, StreamingAdminProperties props, JsonMapper json) {
        this.template = template;
        this.props = props;
        this.json = json;
    }

    public List<Publicado> publicar(PublicarEventosRequest req) {
        List<Publicado> publicados = new ArrayList<>();
        switch (req.escenario()) {
            case VALIDO -> publicados.addAll(pedidoCompleto(nuevoId(), null));
            case INVALIDO -> publicados.add(enviar(Long.toString(nuevoId()), "esto no es JSON", null,
                    "texto que no es JSON: va directo a la DLT"));
            case INCOMPLETO -> publicados.add(enviar(Long.toString(nuevoId()),
                    "{\"eventoId\":\"" + UUID.randomUUID() + "\",\"tipo\":\"PAGO_APROBADO\",\"version\":1}", null,
                    "JSON sin pedidoId ni ocurridoEn: inválido, a la DLT sin reintentos"));
            case FALLO_TRANSITORIO -> publicados.addAll(pedidoCompleto(nuevoId(), "bd-caida").subList(0, 1));
            case LOTE -> {
                int cantidad = Math.min(req.cantidad() == null ? 50 : req.cantidad(), props.maxLote());
                for (int i = 0; i < cantidad; i++) {
                    publicados.addAll(pedidoCompleto(nuevoId(), null));
                }
            }
        }
        log.info("Demo {}: {} eventos publicados en {}", req.escenario(), publicados.size(), props.topicoDemo());
        return publicados;
    }

    /** PEDIDO_CREADO, PAGO_APROBADO y el cambio a ENTREGADO, con la misma key: van a la misma partición. */
    private List<Publicado> pedidoCompleto(long pedidoId, String simularFallo) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        String metodo = PAGOS[r.nextInt(PAGOS.length)];
        List<Map<String, Object>> items = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        int lineas = 1 + r.nextInt(3);
        for (int i = 0; i < lineas; i++) {
            String[] p = PRODUCTOS[r.nextInt(PRODUCTOS.length)];
            int cantidad = 1 + r.nextInt(3);
            BigDecimal precio = new BigDecimal(p[2]);
            total = total.add(precio.multiply(BigDecimal.valueOf(cantidad)));
            items.add(Map.of("productoId", Long.parseLong(p[0]), "nombre", p[1], "cantidad", cantidad, "precioUnitario", precio));
        }
        Instant ahora = Instant.now();
        String key = Long.toString(pedidoId);
        List<Publicado> salida = new ArrayList<>();
        salida.add(enviar(key, evento(pedidoId, "PEDIDO_CREADO", "PENDIENTE", "PENDIENTE", metodo, total, items, ahora),
                simularFallo, simularFallo == null ? "pedido creado" : "pedido creado con fallo transitorio simulado"));
        if (simularFallo == null) {
            salida.add(enviar(key, evento(pedidoId, "PAGO_APROBADO", "PENDIENTE", "PAGADO", metodo, total, List.of(),
                    ahora.plusMillis(200)), null, "pago aprobado"));
            salida.add(enviar(key, evento(pedidoId, "ESTADO_CAMBIADO", "ENTREGADO", "PAGADO", metodo, total, List.of(),
                    ahora.plusMillis(400)), null, "pedido entregado"));
        }
        return salida;
    }

    private String evento(long pedidoId, String tipo, String estado, String estadoPago, String metodo, BigDecimal total,
            List<Map<String, Object>> items, Instant cuando) {
        Map<String, Object> e = new LinkedHashMap<>();
        e.put("eventoId", UUID.randomUUID());
        e.put("version", 1);
        e.put("tipo", tipo);
        e.put("pedidoId", pedidoId);
        e.put("clienteId", "demo");
        e.put("clienteUsername", "demo");
        e.put("estado", estado);
        e.put("estadoPago", estadoPago);
        e.put("metodoPago", metodo);
        e.put("total", total);
        e.put("items", items);
        e.put("ocurridoEn", cuando.toString());
        e.put("origen", "ms-streaming-admin (demo)");
        return json.writeValueAsString(e);
    }

    private Publicado enviar(String key, String valor, String simularFallo, String descripcion) {
        ProducerRecord<String, String> registro = new ProducerRecord<>(props.topicoDemo(), key, valor);
        if (simularFallo != null) {
            registro.headers().add(props.headerSimularFallo(), simularFallo.getBytes(StandardCharsets.UTF_8));
        }
        try {
            RecordMetadata m = template.send(registro).get(props.timeout().toMillis(), TimeUnit.MILLISECONDS).getRecordMetadata();
            return new Publicado(key, m.partition(), m.offset(), descripcion);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new ClusterNoDisponibleException("publicación interrumpida", ex);
        } catch (ExecutionException | TimeoutException ex) {
            throw new ClusterNoDisponibleException("no se pudo publicar en " + props.topicoDemo(), ex);
        }
    }

    private static long nuevoId() {
        return 900_000 + ThreadLocalRandom.current().nextLong(99_999);
    }
}
