package cl.duoc.pedidos360.analitica.proyeccion;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;

import cl.duoc.pedidos360.analitica.evento.ActividadPedido;
import cl.duoc.pedidos360.analitica.evento.TipoActividad;

/**
 * Caso de uso 2 · seguimiento de pedidos. Arma, para cada pedido, la línea de tiempo de sus hechos con la
 * partición y el offset por donde llegó cada uno. Como la key del evento es el pedidoId, todos los hechos de un
 * pedido viven en la misma partición y llegan en orden.
 */
@Service
public class SeguimientoPedidos {

    private static final int PEDIDOS_MAXIMOS = 1_000;

    private final LinkedHashMap<Long, Seguimiento> pedidos = new LinkedHashMap<>();
    private final EventosVistos vistos = new EventosVistos(200_000);

    /** @return true si el evento era nuevo y se agregó a la línea de tiempo */
    public synchronized boolean registrar(ActividadPedido e, int particion, long offset) {
        if (!vistos.registrar(e.eventoId())) {
            return false;
        }
        Seguimiento s = pedidos.computeIfAbsent(e.pedidoId(), id -> new Seguimiento(id, e.clienteId(), e.clienteUsername()));
        s.hitos.add(new Hito(e.tipo(), e.estado(), e.estadoPago(), e.ocurridoEn(), Instant.now(), particion, offset));
        s.hitos.sort(Comparator.comparing(Hito::ocurridoEn));
        Hito ultimo = s.hitos.getLast();
        s.estado = ultimo.estado();
        s.estadoPago = ultimo.estadoPago();
        if (e.total() != null) {
            s.total = e.total();
        }
        s.particion = particion;
        while (pedidos.size() > PEDIDOS_MAXIMOS) {
            pedidos.pollFirstEntry();
        }
        return true;
    }

    public synchronized Optional<Vista> de(Long pedidoId) {
        return Optional.ofNullable(pedidos.get(pedidoId)).map(Seguimiento::vista);
    }

    /** Pedidos más recientes primero; si clienteId es null, de todos los clientes. */
    public synchronized List<Vista> recientes(String clienteId, int limite) {
        List<Vista> lista = new ArrayList<>();
        for (Seguimiento s : pedidos.reversed().values()) {
            if (clienteId == null || clienteId.equals(s.clienteId)) {
                lista.add(s.vista());
                if (lista.size() == limite) {
                    break;
                }
            }
        }
        return lista;
    }

    public synchronized int cantidad() {
        return pedidos.size();
    }

    /** Un hecho de la línea de tiempo y por dónde llegó. */
    public record Hito(TipoActividad tipo, String estado, String estadoPago, Instant ocurridoEn, Instant procesadoEn,
            int particion, long offset) {
    }

    /** Lo que se devuelve por la API. */
    public record Vista(Long pedidoId, String clienteId, String clienteUsername, String estado, String estadoPago,
            BigDecimal total, int particion, List<Hito> hitos) {
    }

    private static final class Seguimiento {
        final Long pedidoId;
        final String clienteId;
        final String clienteUsername;
        final List<Hito> hitos = new ArrayList<>();
        String estado;
        String estadoPago;
        BigDecimal total;
        int particion;

        Seguimiento(Long pedidoId, String clienteId, String clienteUsername) {
            this.pedidoId = pedidoId;
            this.clienteId = clienteId;
            this.clienteUsername = clienteUsername;
        }

        Vista vista() {
            return new Vista(pedidoId, clienteId, clienteUsername, estado, estadoPago, total, particion, List.copyOf(hitos));
        }
    }
}
