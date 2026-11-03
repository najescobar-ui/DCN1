package cl.duoc.pedidos360.mensajeria.dto;

import java.util.List;
import java.util.Map;

/** Respuestas de la API del administrador. */
public final class Respuestas {

    private Respuestas() {
    }

    /** Estado de una cola con sus contadores de mensajes. */
    public record ColaInfo(String nombre, String tipo, boolean durable, String nodo, long mensajes, long listos,
            long sinConfirmar, int consumidores, Map<String, Object> argumentos) {
    }

    public record ExchangeInfo(String nombre, String tipo, boolean durable, boolean interno) {
    }

    public record BindingInfo(String exchange, String destino, String tipoDestino, String routingKey) {
    }

    public record NodoInfo(String nombre, boolean activo, long memoriaUsadaBytes, long uptimeMs) {
    }

    public record ClusterInfo(String nombre, String version, List<NodoInfo> nodos) {
    }

    /** Resultado de mover mensajes de una DLQ de vuelta a su ruta original. */
    public record ReprocesoInfo(String dlq, int reprocesados, int sinDestino) {
    }

    public record PublicacionInfo(String exchange, String routingKey, String messageId) {
    }
}
