package cl.duoc.pedidos360.notificaciones.fallido;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import cl.duoc.pedidos360.notificaciones.mensajeria.ConfirmadorMensajes;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * Registro y reproceso de los mensajes no entregados. Los listeners de las DLQ llaman a {@link #registrar}
 * con cada mensaje; el administrador puede reenviarlo a su exchange de origen con {@link #reprocesar}.
 */
@Service
public class MensajeFallidoService {

    private static final Logger log = LoggerFactory.getLogger(MensajeFallidoService.class);
    private static final int MAX_CUERPO = 10_000;

    private final MensajeFallidoRepository repository;
    private final RabbitTemplate rabbitTemplate;
    private final JsonMapper jsonMapper;

    public MensajeFallidoService(MensajeFallidoRepository repository, RabbitTemplate rabbitTemplate,
            JsonMapper jsonMapper) {
        this.repository = repository;
        this.rabbitTemplate = rabbitTemplate;
        this.jsonMapper = jsonMapper;
    }

    /** Guarda el mensaje recibido en una DLQ con el motivo y el origen que RabbitMQ anota en x-death. */
    @Transactional
    public MensajeFallido registrar(String dlq, Message mensaje) {
        MessageProperties p = mensaje.getMessageProperties();
        Map<String, ?> muerte = primeraMuerte(p);
        String cuerpo = new String(mensaje.getBody(), StandardCharsets.UTF_8);
        if (cuerpo.length() > MAX_CUERPO) {
            cuerpo = cuerpo.substring(0, MAX_CUERPO);
        }
        MensajeFallido fallido = new MensajeFallido(
                dlq,
                texto(muerte.get("queue")),
                texto(muerte.get("reason")),
                muerte.get("count") instanceof Number n ? n.longValue() : 1,
                texto(muerte.get("exchange")),
                primeraRoutingKey(muerte.get("routing-keys"), p.getReceivedRoutingKey()),
                p.getMessageId(),
                p.getContentType(),
                jsonMapper.writeValueAsString(headersOriginales(p)),
                cuerpo);
        return repository.save(fallido);
    }

    public List<MensajeFallido> listar() {
        return repository.findTop200ByOrderByIdDesc();
    }

    /**
     * Reenvía el mensaje a su exchange y routing key de origen, sin los headers de la demostración, y lo
     * marca como reprocesado. Si vuelve a fallar, llegará otra vez a la DLQ y quedará un registro nuevo.
     */
    @Transactional
    public MensajeFallido reprocesar(Long id) {
        MensajeFallido fallido = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No existe el mensaje " + id));
        if (fallido.getReprocesadoEn() != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "El mensaje " + id + " ya fue reprocesado");
        }
        if (fallido.getExchangeOrigen() == null || fallido.getRoutingKeyOrigen() == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "El mensaje " + id + " no tiene origen conocido");
        }
        MessageProperties propiedades = new MessageProperties();
        propiedades.setContentType(fallido.getContentType());
        propiedades.setMessageId(fallido.getMessageId());
        propiedades.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
        leerHeaders(fallido.getHeaders()).forEach((k, v) -> {
            if (!k.equals(ConfirmadorMensajes.HEADER_SIMULAR_FALLO)) {
                propiedades.setHeader(k, v);
            }
        });
        Message mensaje = new Message(fallido.getCuerpo().getBytes(StandardCharsets.UTF_8), propiedades);
        rabbitTemplate.send(fallido.getExchangeOrigen(), fallido.getRoutingKeyOrigen(), mensaje);
        fallido.marcarReprocesado();
        log.info("[dlq] Reprocesado mensaje fallido id={} messageId={} → {} / {}", id, fallido.getMessageId(),
                fallido.getExchangeOrigen(), fallido.getRoutingKeyOrigen());
        return fallido;
    }

    /** RabbitMQ agrega x-death con la historia del mensaje; la primera entrada es la muerte más reciente. */
    private static Map<String, ?> primeraMuerte(MessageProperties p) {
        List<Map<String, ?>> muertes = p.getXDeathHeader();
        return muertes == null || muertes.isEmpty() ? Map.of() : muertes.get(0);
    }

    private static String primeraRoutingKey(Object routingKeys, String porDefecto) {
        if (routingKeys instanceof List<?> lista && !lista.isEmpty()) {
            return String.valueOf(lista.get(0));
        }
        return porDefecto;
    }

    /** Headers del mensaje original: sin los que RabbitMQ agrega al reentregar o al mandarlo a la DLQ. */
    private static Map<String, String> headersOriginales(MessageProperties p) {
        Map<String, String> headers = new LinkedHashMap<>();
        p.getHeaders().forEach((k, v) -> {
            if (!k.startsWith("x-death") && !k.startsWith("x-first-death") && !k.startsWith("x-last-death")
                    && !k.equals("x-delivery-count")) {
                headers.put(k, String.valueOf(v));
            }
        });
        return headers;
    }

    private Map<String, String> leerHeaders(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        return jsonMapper.readValue(json, new TypeReference<Map<String, String>>() {
        });
    }

    private static String texto(Object valor) {
        return valor == null ? null : String.valueOf(valor);
    }
}
