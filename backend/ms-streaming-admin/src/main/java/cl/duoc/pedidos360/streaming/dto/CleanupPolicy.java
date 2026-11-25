package cl.duoc.pedidos360.streaming.dto;

/** Qué hace Kafka con los eventos antiguos de un tópico (cleanup.policy). */
public enum CleanupPolicy {
    /** Borra los segmentos que superan retention.ms o retention.bytes. */
    DELETE("delete"),
    /** Conserva solo el último valor de cada key. */
    COMPACT("compact"),
    /** Las dos: último valor por key, y nada más antiguo que la retención. */
    COMPACT_DELETE("compact,delete");

    private final String valor;

    CleanupPolicy(String valor) {
        this.valor = valor;
    }

    public String valor() {
        return valor;
    }
}
