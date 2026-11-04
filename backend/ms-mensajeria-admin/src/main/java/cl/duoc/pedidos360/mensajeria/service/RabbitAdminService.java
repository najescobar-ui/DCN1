package cl.duoc.pedidos360.mensajeria.service;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Exchange;
import org.springframework.amqp.core.ExchangeBuilder;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.GetResponse;

import cl.duoc.pedidos360.mensajeria.config.AdminProperties;
import cl.duoc.pedidos360.mensajeria.dto.BindingRequest;
import cl.duoc.pedidos360.mensajeria.dto.CrearColaRequest;
import cl.duoc.pedidos360.mensajeria.dto.CrearExchangeRequest;
import cl.duoc.pedidos360.mensajeria.dto.PublicarMensajeRequest;
import cl.duoc.pedidos360.mensajeria.dto.Respuestas.BindingInfo;
import cl.duoc.pedidos360.mensajeria.dto.Respuestas.ClusterInfo;
import cl.duoc.pedidos360.mensajeria.dto.Respuestas.ColaInfo;
import cl.duoc.pedidos360.mensajeria.dto.Respuestas.ExchangeInfo;
import cl.duoc.pedidos360.mensajeria.dto.Respuestas.NodoInfo;
import cl.duoc.pedidos360.mensajeria.dto.Respuestas.PublicacionInfo;
import cl.duoc.pedidos360.mensajeria.dto.Respuestas.ReprocesoInfo;
import cl.duoc.pedidos360.mensajeria.dto.TipoCola;
import cl.duoc.pedidos360.mensajeria.exception.BrokerNoDisponibleException;
import cl.duoc.pedidos360.mensajeria.exception.OperacionRechazadaException;
import cl.duoc.pedidos360.mensajeria.exception.RecursoNoEncontradoException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Toda la administración de RabbitMQ pasa por aquí. Los controladores llaman métodos de alto nivel
 * (crearCola, eliminarExchange, ...) y no conocen RabbitAdmin, la API de management ni los argumentos x-*.
 *
 * Escrituras: protocolo AMQP con RabbitAdmin. Lecturas: API HTTP de management, que entrega contadores
 * de mensajes, bindings y nodos (AMQP no permite listar).
 */
@Service
public class RabbitAdminService {

    private static final Logger log = LoggerFactory.getLogger(RabbitAdminService.class);

    private final RabbitAdmin admin;
    private final RabbitTemplate template;
    private final RestClient management;
    private final AdminProperties props;
    private final JsonMapper jsonMapper;

    public RabbitAdminService(RabbitAdmin admin, RabbitTemplate template, RestClient managementClient,
            AdminProperties props, JsonMapper jsonMapper) {
        this.admin = admin;
        this.template = template;
        this.management = managementClient;
        this.props = props;
        this.jsonMapper = jsonMapper;
    }

    // ===================== Colas =====================

    public List<ColaInfo> listarColas() {
        List<ColaInfo> colas = new ArrayList<>();
        for (JsonNode n : consultar("/api/queues/" + vhost())) {
            colas.add(aColaInfo(n));
        }
        return colas;
    }

    public ColaInfo obtenerCola(String nombre) {
        return aColaInfo(consultarRecurso("/api/queues/" + vhost() + "/" + nombre, "La cola " + nombre + " no existe"));
    }

    /** Crea la cola (y su DLQ si se pide). Rechaza nombres reservados o ya existentes. */
    public ColaInfo crearCola(CrearColaRequest req) {
        exigirNoReservado(req.nombre());
        if (existe("/api/queues/" + vhost() + "/" + req.nombre())) {
            throw new OperacionRechazadaException("La cola " + req.nombre() + " ya existe");
        }
        Map<String, Object> args = new HashMap<>();
        args.put("x-queue-type", req.tipo() == TipoCola.QUORUM ? "quorum" : "classic");
        if (req.ttlMs() != null) {
            args.put("x-message-ttl", req.ttlMs());
        }
        if (req.maxMensajes() != null) {
            args.put("x-max-length", req.maxMensajes());
        }
        if (req.limiteEntregas() != null) {
            args.put("x-delivery-limit", req.limiteEntregas());
        }
        if (req.conDlqActivo()) {
            String dlq = req.nombre() + props.sufijoDlq();
            DirectExchange dlx = ExchangeBuilder.directExchange(props.dlx()).durable(true).build();
            Queue colaDlq = new Queue(dlq, true, false, false, Map.of("x-queue-type", args.get("x-queue-type")));
            declarar(() -> {
                admin.declareExchange(dlx);
                admin.declareQueue(colaDlq);
                admin.declareBinding(BindingBuilder.bind(colaDlq).to(dlx).with(dlq));
            });
            args.put("x-dead-letter-exchange", props.dlx());
            args.put("x-dead-letter-routing-key", dlq);
        }
        declarar(() -> admin.declareQueue(new Queue(req.nombre(), true, false, false, args)));
        log.info("Cola creada: {} {} (dlq={})", req.nombre(), args.get("x-queue-type"), req.conDlqActivo());
        return obtenerCola(req.nombre());
    }

    /**
     * Elimina una cola. Las colas del sistema están protegidas. Con soloSiVacia=true se rechaza si quedan
     * mensajes, para no perderlos por accidente. La revisión se hace aquí porque las colas quorum no
     * admiten el flag if-empty del protocolo.
     */
    public void eliminarCola(String nombre, boolean soloSiVacia) {
        exigirNoProtegido(nombre, "la cola");
        ColaInfo cola = obtenerCola(nombre);
        if (soloSiVacia && cola.mensajes() > 0) {
            throw new OperacionRechazadaException(
                    "La cola " + nombre + " tiene " + cola.mensajes() + " mensajes; vacíala o usa soloSiVacia=false");
        }
        declarar(() -> admin.deleteQueue(nombre));
        log.info("Cola eliminada: {}", nombre);
    }

    /** Vacía la cola y devuelve cuántos mensajes se descartaron. Permitido también en colas del sistema. */
    public int purgarCola(String nombre) {
        obtenerCola(nombre);
        int descartados = admin.purgeQueue(nombre);
        log.info("Cola purgada: {} ({} mensajes)", nombre, descartados);
        return descartados;
    }

    /**
     * Devuelve los mensajes de una DLQ a la cola donde fallaron (según el header x-death), para
     * reprocesarlos cuando se corrigió la causa. Usa basicGet sin autoack: un mensaje solo se borra de la
     * DLQ después de publicarlo de nuevo, así no se pierde si algo falla a mitad de camino.
     */
    public ReprocesoInfo reprocesarDlq(String nombre) {
        if (!nombre.endsWith(props.sufijoDlq())) {
            throw new OperacionRechazadaException("Solo se reprocesan colas " + props.sufijoDlq());
        }
        // Solo los mensajes que había al empezar: si uno vuelve a fallar y regresa a la DLQ, no se toma de nuevo.
        long pendientes = Math.min(obtenerCola(nombre).listos(), props.maxReproceso());
        int[] conteo = new int[2];
        template.execute(canal -> {
            for (int i = 0; i < pendientes; i++) {
                GetResponse r = canal.basicGet(nombre, false);
                if (r == null) {
                    break;
                }
                String colaOrigen = colaOrigen(r.getProps().getHeaders());
                if (colaOrigen == null) {
                    canal.basicNack(r.getEnvelope().getDeliveryTag(), false, true);
                    conteo[1]++;
                    break;
                }
                Map<String, Object> headers = new HashMap<>(r.getProps().getHeaders());
                // Se limpia el historial de rechazos y el conteo de entregas (el mensaje vuelve con sus 3
                // intentos completos) y los headers de la demostración (el fallo simulado
                // representa una caída que ya se resolvió).
                headers.keySet().removeIf(h -> h.startsWith("x-death") || h.startsWith("x-first-death")
                        || h.startsWith("x-last-death") || h.equals("x-delivery-count")
                        || props.headersDemo().contains(h));
                AMQP.BasicProperties propiedades = r.getProps().builder().headers(headers).build();
                // Exchange por defecto + nombre de cola: llega solo a la cola original, no a todas las del topic.
                canal.basicPublish("", colaOrigen, propiedades, r.getBody());
                canal.basicAck(r.getEnvelope().getDeliveryTag(), false);
                conteo[0]++;
            }
            return null;
        });
        log.info("DLQ {} reprocesada: {} mensajes devueltos, {} sin destino", nombre, conteo[0], conteo[1]);
        return new ReprocesoInfo(nombre, conteo[0], conteo[1]);
    }

    // ===================== Exchanges =====================

    public List<ExchangeInfo> listarExchanges() {
        List<ExchangeInfo> lista = new ArrayList<>();
        for (JsonNode n : consultar("/api/exchanges/" + vhost())) {
            String nombre = n.path("name").asString();
            if (!nombre.isEmpty()) { // el exchange por defecto no tiene nombre ni se administra
                lista.add(aExchangeInfo(n));
            }
        }
        return lista;
    }

    public ExchangeInfo obtenerExchange(String nombre) {
        return aExchangeInfo(consultarRecurso("/api/exchanges/" + vhost() + "/" + nombre,
                "El exchange " + nombre + " no existe"));
    }

    public ExchangeInfo crearExchange(CrearExchangeRequest req) {
        exigirNoReservado(req.nombre());
        if (existe("/api/exchanges/" + vhost() + "/" + req.nombre())) {
            throw new OperacionRechazadaException("El exchange " + req.nombre() + " ya existe");
        }
        Exchange exchange = new ExchangeBuilder(req.nombre(), req.tipo().amqp()).durable(req.esDurable()).build();
        declarar(() -> admin.declareExchange(exchange));
        log.info("Exchange creado: {} ({})", req.nombre(), req.tipo().amqp());
        return obtenerExchange(req.nombre());
    }

    public void eliminarExchange(String nombre) {
        exigirNoProtegido(nombre, "el exchange");
        obtenerExchange(nombre);
        declarar(() -> admin.deleteExchange(nombre));
        log.info("Exchange eliminado: {}", nombre);
    }

    // ===================== Bindings =====================

    public List<BindingInfo> listarBindings() {
        List<BindingInfo> lista = new ArrayList<>();
        for (JsonNode n : consultar("/api/bindings/" + vhost())) {
            if (!n.path("source").asString().isEmpty()) { // los del exchange por defecto son implícitos
                lista.add(new BindingInfo(n.path("source").asString(), n.path("destination").asString(),
                        n.path("destination_type").asString(), n.path("routing_key").asString()));
            }
        }
        return lista;
    }

    public BindingInfo crearBinding(BindingRequest req) {
        obtenerExchange(req.exchange());
        obtenerCola(req.cola());
        if (buscarBinding(req)) {
            throw new OperacionRechazadaException("El binding ya existe");
        }
        declarar(() -> admin.declareBinding(aBinding(req)));
        log.info("Binding creado: {} -[{}]-> {}", req.exchange(), req.routingKey(), req.cola());
        return new BindingInfo(req.exchange(), req.cola(), "queue", req.routingKey());
    }

    /** Los bindings entre objetos protegidos forman la topología del sistema y no se eliminan. */
    public void eliminarBinding(BindingRequest req) {
        if (props.esProtegido(req.exchange()) && props.esProtegido(req.cola())) {
            throw new OperacionRechazadaException("El binding es parte de la topología del sistema y no se puede eliminar");
        }
        if (!buscarBinding(req)) {
            throw new RecursoNoEncontradoException("No existe el binding " + req.exchange() + " -[" + req.routingKey()
                    + "]-> " + req.cola());
        }
        declarar(() -> admin.removeBinding(aBinding(req)));
        log.info("Binding eliminado: {} -[{}]-> {}", req.exchange(), req.routingKey(), req.cola());
    }

    // ===================== Mensajes y clúster =====================

    /** Publica un mensaje de prueba para verificar una ruta (exchange + routing key). */
    public PublicacionInfo publicar(PublicarMensajeRequest req) {
        obtenerExchange(req.exchange());
        MessageProperties propiedades = new MessageProperties();
        propiedades.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        propiedades.setMessageId(UUID.randomUUID().toString());
        if (req.headers() != null) {
            req.headers().forEach(propiedades::setHeader);
        }
        Message mensaje = new Message(req.cuerpo().getBytes(StandardCharsets.UTF_8), propiedades);
        try {
            template.send(req.exchange(), req.routingKey(), mensaje);
        } catch (AmqpException ex) {
            throw new BrokerNoDisponibleException("No se pudo publicar", ex);
        }
        log.info("Mensaje de prueba {} publicado en {} [{}]", propiedades.getMessageId(), req.exchange(), req.routingKey());
        return new PublicacionInfo(req.exchange(), req.routingKey(), propiedades.getMessageId());
    }

    public ClusterInfo cluster() {
        JsonNode overview = consultar("/api/overview");
        List<NodoInfo> nodos = new ArrayList<>();
        for (JsonNode n : consultar("/api/nodes")) {
            nodos.add(new NodoInfo(n.path("name").asString(), n.path("running").asBoolean(),
                    n.path("mem_used").asLong(), n.path("uptime").asLong()));
        }
        return new ClusterInfo(overview.path("cluster_name").asString(), overview.path("rabbitmq_version").asString(),
                nodos);
    }

    // ===================== Apoyo =====================

    private void exigirNoReservado(String nombre) {
        if (nombre.startsWith(props.prefijoReservado())) {
            throw new OperacionRechazadaException("El prefijo " + props.prefijoReservado() + " está reservado por RabbitMQ");
        }
    }

    private void exigirNoProtegido(String nombre, String que) {
        if (props.esProtegido(nombre)) {
            throw new OperacionRechazadaException("No se puede eliminar " + que + " " + nombre + ": es parte del sistema");
        }
    }

    private boolean buscarBinding(BindingRequest req) {
        JsonNode lista = consultar("/api/bindings/" + vhost() + "/e/" + req.exchange() + "/q/" + req.cola());
        for (JsonNode n : lista) {
            if (req.routingKey().equals(n.path("routing_key").asString())) {
                return true;
            }
        }
        return false;
    }

    private static Binding aBinding(BindingRequest req) {
        return new Binding(req.cola(), Binding.DestinationType.QUEUE, req.exchange(), req.routingKey(), null);
    }

    /** Ejecuta una operación AMQP y traduce los rechazos del broker a errores de la API. */
    private void declarar(Runnable operacion) {
        try {
            operacion.run();
        } catch (org.springframework.amqp.AmqpConnectException ex) {
            throw new BrokerNoDisponibleException("RabbitMQ no está disponible", ex);
        } catch (AmqpException ex) {
            Throwable causa = ex;
            while (causa.getCause() != null) {
                causa = causa.getCause();
            }
            throw new OperacionRechazadaException("RabbitMQ rechazó la operación: " + causa.getMessage());
        }
    }

    /** Cola en la que el mensaje fue rechazado, según el primer registro de x-death. */
    @SuppressWarnings("unchecked")
    private static String colaOrigen(Map<String, Object> headers) {
        if (headers == null || !(headers.get("x-death") instanceof List<?> muertes) || muertes.isEmpty()) {
            return null;
        }
        Object cola = ((Map<String, Object>) muertes.get(0)).get("queue");
        return cola == null ? null : cola.toString();
    }

    private ColaInfo aColaInfo(JsonNode n) {
        @SuppressWarnings("unchecked")
        Map<String, Object> argumentos = jsonMapper.convertValue(n.path("arguments"), Map.class);
        return new ColaInfo(n.path("name").asString(), n.path("type").asString(), n.path("durable").asBoolean(),
                n.path("node").asString(), n.path("messages").asLong(), n.path("messages_ready").asLong(),
                n.path("messages_unacknowledged").asLong(), n.path("consumers").asInt(), argumentos);
    }

    private static ExchangeInfo aExchangeInfo(JsonNode n) {
        return new ExchangeInfo(n.path("name").asString(), n.path("type").asString(), n.path("durable").asBoolean(),
                n.path("internal").asBoolean());
    }

    private String vhost() {
        return URLEncoder.encode(props.vhost(), StandardCharsets.UTF_8);
    }

    private boolean existe(String ruta) {
        try {
            consultar(ruta);
            return true;
        } catch (RecursoNoEncontradoException ex) {
            return false;
        }
    }

    private JsonNode consultarRecurso(String ruta, String mensajeNoEncontrado) {
        try {
            return consultar(ruta);
        } catch (RecursoNoEncontradoException ex) {
            throw new RecursoNoEncontradoException(mensajeNoEncontrado);
        }
    }

    /** GET a la API de management. La ruta ya va codificada (los nombres se validan antes de llegar aquí). */
    private JsonNode consultar(String ruta) {
        try {
            JsonNode cuerpo = management.get().uri(URI.create(props.managementUrl() + ruta)).retrieve()
                    .body(JsonNode.class);
            return cuerpo == null ? jsonMapper.createArrayNode() : cuerpo;
        } catch (HttpClientErrorException.NotFound ex) {
            throw new RecursoNoEncontradoException("No existe: " + ruta);
        } catch (ResourceAccessException ex) {
            throw new BrokerNoDisponibleException("La API de management de RabbitMQ no responde", ex);
        }
    }
}
