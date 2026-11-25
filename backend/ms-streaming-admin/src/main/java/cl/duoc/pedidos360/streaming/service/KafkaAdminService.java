package cl.duoc.pedidos360.streaming.service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.AlterConfigOp;
import org.apache.kafka.clients.admin.Config;
import org.apache.kafka.clients.admin.ConfigEntry;
import org.apache.kafka.clients.admin.ConsumerGroupDescription;
import org.apache.kafka.clients.admin.DescribeClusterResult;
import org.apache.kafka.clients.admin.ListOffsetsResult.ListOffsetsResultInfo;
import org.apache.kafka.clients.admin.ListTopicsOptions;
import org.apache.kafka.clients.admin.MemberDescription;
import org.apache.kafka.clients.admin.NewPartitions;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.admin.OffsetSpec;
import org.apache.kafka.clients.admin.TopicDescription;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.GroupState;
import org.apache.kafka.common.KafkaFuture;
import org.apache.kafka.common.Node;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.TopicPartitionInfo;
import org.apache.kafka.common.config.ConfigResource;
import org.apache.kafka.common.config.TopicConfig;
import org.apache.kafka.common.errors.ApiException;
import org.apache.kafka.common.errors.GroupIdNotFoundException;
import org.apache.kafka.common.errors.GroupNotEmptyException;
import org.apache.kafka.common.errors.InvalidConfigurationException;
import org.apache.kafka.common.errors.InvalidPartitionsException;
import org.apache.kafka.common.errors.InvalidReplicationFactorException;
import org.apache.kafka.common.errors.InvalidRequestException;
import org.apache.kafka.common.errors.PolicyViolationException;
import org.apache.kafka.common.errors.TopicExistsException;
import org.apache.kafka.common.errors.UnknownMemberIdException;
import org.apache.kafka.common.errors.UnknownTopicOrPartitionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import cl.duoc.pedidos360.streaming.config.StreamingAdminProperties;
import cl.duoc.pedidos360.streaming.dto.CleanupPolicy;
import cl.duoc.pedidos360.streaming.dto.CrearTopicoRequest;
import cl.duoc.pedidos360.streaming.dto.ModificarTopicoRequest;
import cl.duoc.pedidos360.streaming.dto.ResetOffsetsRequest;
import cl.duoc.pedidos360.streaming.dto.Respuestas.BrokerInfo;
import cl.duoc.pedidos360.streaming.dto.Respuestas.CambioOffset;
import cl.duoc.pedidos360.streaming.dto.Respuestas.ClusterInfo;
import cl.duoc.pedidos360.streaming.dto.Respuestas.GrupoDetalle;
import cl.duoc.pedidos360.streaming.dto.Respuestas.GrupoResumen;
import cl.duoc.pedidos360.streaming.dto.Respuestas.LagInfo;
import cl.duoc.pedidos360.streaming.dto.Respuestas.MiembroInfo;
import cl.duoc.pedidos360.streaming.dto.Respuestas.ParticionInfo;
import cl.duoc.pedidos360.streaming.dto.Respuestas.ResetResultado;
import cl.duoc.pedidos360.streaming.dto.Respuestas.TopicoDetalle;
import cl.duoc.pedidos360.streaming.dto.Respuestas.TopicoResumen;
import cl.duoc.pedidos360.streaming.exception.ClusterNoDisponibleException;
import cl.duoc.pedidos360.streaming.exception.OperacionInvalidaException;
import cl.duoc.pedidos360.streaming.exception.OperacionRechazadaException;
import cl.duoc.pedidos360.streaming.exception.RecursoNoEncontradoException;

/**
 * Toda la administración de Kafka pasa por aquí: es la única clase que conoce el cliente Admin. Los controladores
 * solo validan la entrada y llaman a estos métodos.
 */
@Service
public class KafkaAdminService {

    private static final Logger log = LoggerFactory.getLogger(KafkaAdminService.class);

    /** Configuraciones de tópico que se muestran siempre, aunque tengan el valor por defecto del broker. */
    private static final List<String> CONFIG_VISIBLE = List.of(TopicConfig.CLEANUP_POLICY_CONFIG,
            TopicConfig.RETENTION_MS_CONFIG, TopicConfig.RETENTION_BYTES_CONFIG, TopicConfig.SEGMENT_MS_CONFIG,
            TopicConfig.MIN_IN_SYNC_REPLICAS_CONFIG, TopicConfig.MIN_COMPACTION_LAG_MS_CONFIG,
            TopicConfig.UNCLEAN_LEADER_ELECTION_ENABLE_CONFIG, TopicConfig.MAX_MESSAGE_BYTES_CONFIG);

    private final Admin admin;
    private final StreamingAdminProperties props;

    public KafkaAdminService(Admin admin, StreamingAdminProperties props) {
        this.admin = admin;
        this.props = props;
    }

    // ---------- Clúster ----------

    public ClusterInfo cluster() {
        DescribeClusterResult r = admin.describeCluster();
        Node controlador = esperar(r.controller());
        List<BrokerInfo> brokers = esperar(r.nodes()).stream()
                .sorted(Comparator.comparingInt(Node::id))
                .map(n -> new BrokerInfo(n.id(), n.host(), n.port(), n.rack(), controlador != null && n.id() == controlador.id()))
                .toList();
        int topicos = esperar(admin.listTopics().names()).size();
        int grupos = esperar(admin.listConsumerGroups().all()).size();
        return new ClusterInfo(esperar(r.clusterId()), controlador == null ? null : controlador.id(), brokers, topicos, grupos);
    }

    // ---------- Tópicos ----------

    public List<TopicoResumen> listarTopicos() {
        Set<String> nombres = esperar(admin.listTopics(new ListTopicsOptions().listInternal(true)).names());
        Map<String, TopicDescription> descripciones = esperar(admin.describeTopics(nombres).allTopicNames());
        Map<String, Config> configs = configuraciones(nombres);
        Map<TopicPartition, Long[]> offsets = offsets(descripciones.values());
        return descripciones.values().stream()
                .sorted(Comparator.comparing((TopicDescription d) -> props.esInterno(d.name())).thenComparing(TopicDescription::name))
                .map(d -> {
                    long mensajes = d.partitions().stream()
                            .mapToLong(p -> retenidos(offsets.get(new TopicPartition(d.name(), p.partition())))).sum();
                    Config c = configs.get(d.name());
                    return new TopicoResumen(d.name(), d.isInternal() || props.esInterno(d.name()), props.esProtegido(d.name()),
                            d.partitions().size(), replicacion(d), isrCompleto(d), mensajes,
                            valor(c, TopicConfig.CLEANUP_POLICY_CONFIG), valor(c, TopicConfig.RETENTION_MS_CONFIG));
                })
                .toList();
    }

    public TopicoDetalle obtenerTopico(String nombre) {
        TopicDescription d = describir(nombre);
        Map<TopicPartition, Long[]> offsets = offsets(List.of(d));
        List<ParticionInfo> particiones = d.partitions().stream()
                .map(p -> {
                    Long[] o = offsets.get(new TopicPartition(nombre, p.partition()));
                    return new ParticionInfo(p.partition(), p.leader() == null ? -1 : p.leader().id(), ids(p.replicas()),
                            ids(p.isr()), o == null ? 0 : o[0], o == null ? 0 : o[1], retenidos(o));
                })
                .toList();
        Config c = configuraciones(Set.of(nombre)).get(nombre);
        Map<String, String> configuracion = new TreeMap<>();
        if (c != null) {
            for (ConfigEntry e : c.entries()) {
                if (CONFIG_VISIBLE.contains(e.name()) || e.source() == ConfigEntry.ConfigSource.DYNAMIC_TOPIC_CONFIG) {
                    configuracion.put(e.name(), e.value());
                }
            }
        }
        return new TopicoDetalle(nombre, d.isInternal() || props.esInterno(nombre), props.esProtegido(nombre),
                replicacion(d), particiones, configuracion);
    }

    public TopicoDetalle crearTopico(CrearTopicoRequest req) {
        Map<String, String> config = new LinkedHashMap<>();
        config.put(TopicConfig.CLEANUP_POLICY_CONFIG,
                (req.cleanupPolicy() == null ? CleanupPolicy.DELETE : req.cleanupPolicy()).valor());
        config.put(TopicConfig.MIN_IN_SYNC_REPLICAS_CONFIG,
                Integer.toString(req.minInsync() != null ? req.minInsync() : Math.min(2, req.replicacion())));
        if (req.retencionMs() != null) {
            config.put(TopicConfig.RETENTION_MS_CONFIG, req.retencionMs().toString());
        }
        if (req.retencionBytes() != null) {
            config.put(TopicConfig.RETENTION_BYTES_CONFIG, req.retencionBytes().toString());
        }
        NewTopic nuevo = new NewTopic(req.nombre(), req.particiones(), req.replicacion()).configs(config);
        esperar(admin.createTopics(List.of(nuevo)).all(), "el tópico " + req.nombre());
        log.info("Tópico creado: {} ({} particiones, {} réplicas, {})", req.nombre(), req.particiones(), req.replicacion(), config);
        return obtenerTopico(req.nombre());
    }

    public TopicoDetalle modificarTopico(String nombre, ModificarTopicoRequest req) {
        TopicDescription d = describir(nombre);
        if (props.esInterno(nombre)) {
            throw new OperacionRechazadaException("El tópico " + nombre + " es interno de Kafka y no se modifica desde la API");
        }
        if (req.minInsync() != null && req.minInsync() > replicacion(d)) {
            throw new OperacionInvalidaException("minInsync (" + req.minInsync() + ") no puede superar las réplicas del tópico ("
                    + replicacion(d) + ")");
        }
        List<AlterConfigOp> cambios = new ArrayList<>();
        if (req.retencionMs() != null) {
            cambios.add(set(TopicConfig.RETENTION_MS_CONFIG, req.retencionMs().toString()));
        }
        if (req.retencionBytes() != null) {
            cambios.add(set(TopicConfig.RETENTION_BYTES_CONFIG, req.retencionBytes().toString()));
        }
        if (req.cleanupPolicy() != null) {
            cambios.add(set(TopicConfig.CLEANUP_POLICY_CONFIG, req.cleanupPolicy().valor()));
        }
        if (req.minInsync() != null) {
            cambios.add(set(TopicConfig.MIN_IN_SYNC_REPLICAS_CONFIG, req.minInsync().toString()));
        }
        esperar(admin.incrementalAlterConfigs(Map.of(new ConfigResource(ConfigResource.Type.TOPIC, nombre), cambios)).all(),
                "el tópico " + nombre);
        log.info("Configuración de {} modificada: {}", nombre, cambios);
        return obtenerTopico(nombre);
    }

    public TopicoDetalle aumentarParticiones(String nombre, int total) {
        TopicDescription d = describir(nombre);
        if (props.esInterno(nombre)) {
            throw new OperacionRechazadaException("El tópico " + nombre + " es interno de Kafka");
        }
        if (total <= d.partitions().size()) {
            throw new OperacionInvalidaException("El tópico ya tiene " + d.partitions().size()
                    + " particiones; Kafka solo permite aumentarlas");
        }
        esperar(admin.createPartitions(Map.of(nombre, NewPartitions.increaseTo(total))).all(), "el tópico " + nombre);
        log.info("Particiones de {}: {} -> {}", nombre, d.partitions().size(), total);
        return obtenerTopico(nombre);
    }

    public void eliminarTopico(String nombre) {
        if (props.esProtegido(nombre)) {
            throw new OperacionRechazadaException("El tópico " + nombre + " es parte del sistema y no se puede eliminar");
        }
        describir(nombre);
        esperar(admin.deleteTopics(List.of(nombre)).all(), "el tópico " + nombre);
        log.info("Tópico eliminado: {}", nombre);
    }

    // ---------- Consumer groups ----------

    public List<GrupoResumen> listarGrupos() {
        List<String> ids = esperar(admin.listConsumerGroups().all()).stream().map(g -> g.groupId()).sorted().toList();
        if (ids.isEmpty()) {
            return List.of();
        }
        Map<String, ConsumerGroupDescription> descripciones = esperar(admin.describeConsumerGroups(ids).all());
        List<GrupoResumen> grupos = new ArrayList<>();
        for (String id : ids) {
            ConsumerGroupDescription g = descripciones.get(id);
            List<LagInfo> lag = lag(id, g);
            Set<String> topicos = new TreeSet<>();
            lag.forEach(l -> topicos.add(l.topico()));
            grupos.add(new GrupoResumen(id, estado(g), g.members().size(), g.coordinator() == null ? null : g.coordinator().id(),
                    List.copyOf(topicos), lag.stream().mapToLong(LagInfo::lag).sum()));
        }
        return grupos;
    }

    public GrupoDetalle obtenerGrupo(String id) {
        ConsumerGroupDescription g = describirGrupo(id);
        List<LagInfo> lag = lag(id, g);
        List<MiembroInfo> miembros = g.members().stream()
                .sorted(Comparator.comparing(MemberDescription::clientId))
                .map(m -> new MiembroInfo(m.clientId(), m.consumerId(), m.host(), m.assignment().topicPartitions().stream()
                        .sorted(Comparator.comparing(TopicPartition::topic).thenComparingInt(TopicPartition::partition))
                        .map(TopicPartition::toString).toList()))
                .toList();
        return new GrupoDetalle(id, estado(g), g.partitionAssignor(), g.coordinator() == null ? null : g.coordinator().id(),
                miembros, lag, lag.stream().mapToLong(LagInfo::lag).sum());
    }

    /**
     * Mueve los offsets confirmados del grupo. Kafka solo lo permite si el grupo no tiene consumidores activos (si
     * no, ellos mismos volverían a confirmar sus offsets): primero hay que detenerlo.
     */
    public ResetResultado resetearOffsets(String id, ResetOffsetsRequest req) {
        ConsumerGroupDescription g = describirGrupo(id);
        if (!g.members().isEmpty()) {
            throw new OperacionRechazadaException("El grupo " + id + " tiene " + g.members().size()
                    + " consumidores activos; detenlo antes de mover sus offsets");
        }
        TopicDescription d = describir(req.topico());
        List<TopicPartition> particiones = d.partitions().stream()
                .map(p -> new TopicPartition(req.topico(), p.partition())).toList();
        OffsetSpec spec = switch (req.destino()) {
            case INICIO -> OffsetSpec.earliest();
            case FIN -> OffsetSpec.latest();
            case FECHA -> OffsetSpec.forTimestamp(req.desde().toEpochMilli());
        };
        Map<TopicPartition, OffsetSpec> pedido = new HashMap<>();
        particiones.forEach(tp -> pedido.put(tp, spec));
        Map<TopicPartition, ListOffsetsResultInfo> destino = esperar(admin.listOffsets(pedido).all());
        Map<TopicPartition, Long> fin = new HashMap<>();
        if (req.destino() == ResetOffsetsRequest.Destino.FECHA) {
            // Si no hay eventos después de la fecha, Kafka responde -1: el grupo queda al final.
            Map<TopicPartition, OffsetSpec> ultimos = new HashMap<>();
            particiones.forEach(tp -> ultimos.put(tp, OffsetSpec.latest()));
            esperar(admin.listOffsets(ultimos).all()).forEach((tp, info) -> fin.put(tp, info.offset()));
        }
        Map<TopicPartition, OffsetAndMetadata> antes = esperar(admin.listConsumerGroupOffsets(id).partitionsToOffsetAndMetadata());
        Map<TopicPartition, OffsetAndMetadata> nuevos = new HashMap<>();
        List<CambioOffset> cambios = new ArrayList<>();
        for (TopicPartition tp : particiones) {
            long offset = destino.get(tp).offset();
            if (offset < 0) {
                offset = fin.getOrDefault(tp, 0L);
            }
            nuevos.put(tp, new OffsetAndMetadata(offset));
            OffsetAndMetadata previo = antes.get(tp);
            cambios.add(new CambioOffset(tp.partition(), previo == null ? -1 : previo.offset(), offset));
        }
        esperar(admin.alterConsumerGroupOffsets(id, nuevos).all(), "el grupo " + id);
        log.info("Offsets de {} en {} movidos a {}: {}", id, req.topico(), req.destino(), cambios);
        return new ResetResultado(id, req.topico(), req.destino().name(), cambios);
    }

    // ---------- Apoyo ----------

    private TopicDescription describir(String nombre) {
        return esperar(admin.describeTopics(List.of(nombre)).allTopicNames(), "el tópico " + nombre).get(nombre);
    }

    private ConsumerGroupDescription describirGrupo(String id) {
        ConsumerGroupDescription g = esperar(admin.describeConsumerGroups(List.of(id)).all(), "el grupo " + id).get(id);
        // Kafka responde con estado DEAD (en vez de un error) para un grupo que no existe.
        if (g == null || g.groupState() == GroupState.DEAD) {
            throw new RecursoNoEncontradoException("No existe el grupo " + id);
        }
        return g;
    }

    /** Lag por partición: offset final del tópico menos el último offset confirmado por el grupo. */
    private List<LagInfo> lag(String id, ConsumerGroupDescription g) {
        Map<TopicPartition, OffsetAndMetadata> confirmados = esperar(admin.listConsumerGroupOffsets(id).partitionsToOffsetAndMetadata());
        Map<TopicPartition, String> duenios = new HashMap<>();
        g.members().forEach(m -> m.assignment().topicPartitions().forEach(tp -> duenios.put(tp, m.clientId())));
        Set<TopicPartition> particiones = new java.util.HashSet<>(confirmados.keySet());
        particiones.addAll(duenios.keySet());
        if (particiones.isEmpty()) {
            return List.of();
        }
        Map<TopicPartition, OffsetSpec> pedido = new HashMap<>();
        particiones.forEach(tp -> pedido.put(tp, OffsetSpec.latest()));
        Map<TopicPartition, ListOffsetsResultInfo> fin = esperar(admin.listOffsets(pedido).all());
        return particiones.stream()
                .sorted(Comparator.comparing(TopicPartition::topic).thenComparingInt(TopicPartition::partition))
                .map(tp -> {
                    OffsetAndMetadata c = confirmados.get(tp);
                    long finTp = fin.get(tp).offset();
                    long confirmado = c == null ? -1 : c.offset();
                    long lag = c == null ? finTp : Math.max(0, finTp - confirmado);
                    return new LagInfo(tp.topic(), tp.partition(), confirmado, finTp, lag, duenios.getOrDefault(tp, ""));
                })
                .toList();
    }

    /** [offset inicial, offset final] de cada partición. */
    private Map<TopicPartition, Long[]> offsets(Collection<TopicDescription> topicos) {
        Map<TopicPartition, OffsetSpec> inicio = new HashMap<>();
        Map<TopicPartition, OffsetSpec> fin = new HashMap<>();
        for (TopicDescription d : topicos) {
            for (TopicPartitionInfo p : d.partitions()) {
                TopicPartition tp = new TopicPartition(d.name(), p.partition());
                inicio.put(tp, OffsetSpec.earliest());
                fin.put(tp, OffsetSpec.latest());
            }
        }
        Map<TopicPartition, Long[]> resultado = new HashMap<>();
        if (inicio.isEmpty()) {
            return resultado;
        }
        Map<TopicPartition, ListOffsetsResultInfo> i = esperar(admin.listOffsets(inicio).all());
        Map<TopicPartition, ListOffsetsResultInfo> f = esperar(admin.listOffsets(fin).all());
        i.forEach((tp, info) -> resultado.put(tp, new Long[] { info.offset(), f.get(tp).offset() }));
        return resultado;
    }

    private Map<String, Config> configuraciones(Collection<String> topicos) {
        List<ConfigResource> recursos = topicos.stream().map(t -> new ConfigResource(ConfigResource.Type.TOPIC, t)).toList();
        Map<String, Config> resultado = new HashMap<>();
        esperar(admin.describeConfigs(recursos).all()).forEach((r, c) -> resultado.put(r.name(), c));
        return resultado;
    }

    private static long retenidos(Long[] o) {
        return o == null ? 0 : Math.max(0, o[1] - o[0]);
    }

    private static int replicacion(TopicDescription d) {
        return d.partitions().isEmpty() ? 0 : d.partitions().getFirst().replicas().size();
    }

    private static boolean isrCompleto(TopicDescription d) {
        return d.partitions().stream().allMatch(p -> p.isr().size() == p.replicas().size());
    }

    private static List<Integer> ids(List<Node> nodos) {
        return nodos.stream().map(Node::id).toList();
    }

    private static String valor(Config c, String clave) {
        return Optional.ofNullable(c).map(x -> x.get(clave)).map(ConfigEntry::value).orElse(null);
    }

    private static String estado(ConsumerGroupDescription g) {
        return g.groupState() == null ? "UNKNOWN" : g.groupState().name();
    }

    private static AlterConfigOp set(String clave, String valor) {
        return new AlterConfigOp(new ConfigEntry(clave, valor), AlterConfigOp.OpType.SET);
    }

    private <T> T esperar(KafkaFuture<T> futuro) {
        return esperar(futuro, null);
    }

    /** Espera la respuesta del clúster y traduce los errores de Kafka a los de la API. */
    private <T> T esperar(KafkaFuture<T> futuro, String recurso) {
        try {
            return futuro.get(props.timeout().toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new ClusterNoDisponibleException("operación interrumpida", ex);
        } catch (TimeoutException ex) {
            throw new ClusterNoDisponibleException("el clúster no respondió a tiempo", ex);
        } catch (ExecutionException ex) {
            Throwable c = ex.getCause();
            String que = recurso == null ? "el recurso" : recurso;
            if (c instanceof UnknownTopicOrPartitionException || c instanceof GroupIdNotFoundException) {
                throw new RecursoNoEncontradoException("No existe " + que);
            }
            if (c instanceof TopicExistsException) {
                throw new OperacionRechazadaException("Ya existe " + que);
            }
            if (c instanceof GroupNotEmptyException || c instanceof UnknownMemberIdException) {
                throw new OperacionRechazadaException(que + " tiene consumidores activos; detenlo primero");
            }
            if (c instanceof InvalidReplicationFactorException || c instanceof InvalidPartitionsException
                    || c instanceof InvalidConfigurationException || c instanceof PolicyViolationException
                    || c instanceof InvalidRequestException) {
                throw new OperacionInvalidaException(c.getMessage());
            }
            if (c instanceof org.apache.kafka.common.errors.RetriableException) {
                throw new ClusterNoDisponibleException(c.getMessage(), ex);
            }
            if (c instanceof ApiException api) {
                throw new OperacionInvalidaException(api.getMessage());
            }
            throw new ClusterNoDisponibleException(c == null ? ex.getMessage() : c.getMessage(), ex);
        }
    }
}
