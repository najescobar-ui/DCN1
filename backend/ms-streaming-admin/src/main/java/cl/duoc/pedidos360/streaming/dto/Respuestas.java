package cl.duoc.pedidos360.streaming.dto;

import java.util.List;
import java.util.Map;

/** Respuestas de la API del administrador. */
public final class Respuestas {

    private Respuestas() {
    }

    public record ClusterInfo(String clusterId, Integer controlador, List<BrokerInfo> brokers, int topicos, int grupos) {
    }

    public record BrokerInfo(int id, String host, int puerto, String rack, boolean controlador) {
    }

    /**
     * @param isrCompleto todas las particiones tienen todas sus réplicas en sincronía
     * @param mensajes    eventos retenidos (suma de offset final menos offset inicial)
     */
    public record TopicoResumen(String nombre, boolean interno, boolean protegido, int particiones, int replicacion,
            boolean isrCompleto, long mensajes, String cleanupPolicy, String retencionMs) {
    }

    public record TopicoDetalle(String nombre, boolean interno, boolean protegido, int replicacion,
            List<ParticionInfo> particiones, Map<String, String> configuracion) {
    }

    /** @param lider id del broker líder; -1 si la partición no tiene líder disponible */
    public record ParticionInfo(int particion, int lider, List<Integer> replicas, List<Integer> isr, long offsetInicio,
            long offsetFin, long mensajes) {
    }

    public record GrupoResumen(String id, String estado, int miembros, Integer coordinador, List<String> topicos,
            long lagTotal) {
    }

    public record GrupoDetalle(String id, String estado, String asignador, Integer coordinador,
            List<MiembroInfo> miembros, List<LagInfo> particiones, long lagTotal) {
    }

    public record MiembroInfo(String clientId, String consumerId, String host, List<String> particiones) {
    }

    /**
     * @param confirmado último offset confirmado por el grupo (-1 si nunca confirmó)
     * @param fin        offset donde se escribirá el próximo evento
     * @param lag        eventos que el grupo aún no procesa
     * @param miembro    client id del consumidor que tiene la partición (vacío si nadie)
     */
    public record LagInfo(String topico, int particion, long confirmado, long fin, long lag, String miembro) {
    }

    public record ResetResultado(String grupo, String topico, String destino, List<CambioOffset> particiones) {
    }

    public record CambioOffset(int particion, long antes, long despues) {
    }

    /** Un evento publicado por la demo: dónde quedó. */
    public record Publicado(String key, int particion, long offset, String descripcion) {
    }
}
