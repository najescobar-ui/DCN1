package cl.duoc.pedidos360.analitica.proyeccion;

import java.util.LinkedHashSet;
import java.util.SequencedSet;
import java.util.UUID;

/**
 * Recuerda los últimos eventoId procesados para que una proyección no cuente dos veces el mismo hecho. Pasa en
 * dos casos normales de Kafka: al rehidratar desde el log y luego retomar desde el offset del grupo, y cuando un
 * rebalance entrega a otro consumidor un lote cuyo offset aún no se había confirmado (entrega al menos una vez).
 */
final class EventosVistos {

    private final int capacidad;
    private final SequencedSet<UUID> vistos = new LinkedHashSet<>();

    EventosVistos(int capacidad) {
        this.capacidad = capacidad;
    }

    /** @return true si es la primera vez que se ve este evento */
    boolean registrar(UUID eventoId) {
        if (!vistos.add(eventoId)) {
            return false;
        }
        if (vistos.size() > capacidad) {
            vistos.removeFirst();
        }
        return true;
    }

    int cantidad() {
        return vistos.size();
    }
}
