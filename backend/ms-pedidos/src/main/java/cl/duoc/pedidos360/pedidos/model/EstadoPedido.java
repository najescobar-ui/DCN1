package cl.duoc.pedidos360.pedidos.model;

import java.util.Set;

public enum EstadoPedido {
    PENDIENTE, CONFIRMADO, DESPACHADO, ENTREGADO, CANCELADO;

    /**
     * Transiciones permitidas: el pedido solo avanza en orden, y solo se puede cancelar antes
     * de salir a reparto. ENTREGADO y CANCELADO son estados finales.
     */
    public Set<EstadoPedido> siguientes() {
        return switch (this) {
            case PENDIENTE -> Set.of(CONFIRMADO, CANCELADO);
            case CONFIRMADO -> Set.of(DESPACHADO, CANCELADO);
            case DESPACHADO -> Set.of(ENTREGADO);
            case ENTREGADO, CANCELADO -> Set.of();
        };
    }

    public boolean puedePasarA(EstadoPedido nuevo) {
        return siguientes().contains(nuevo);
    }
}
