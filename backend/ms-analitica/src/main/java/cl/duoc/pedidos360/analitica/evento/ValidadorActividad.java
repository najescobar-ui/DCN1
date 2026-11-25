package cl.duoc.pedidos360.analitica.evento;

import java.util.ArrayList;
import java.util.List;

/** Reglas mínimas que debe cumplir un evento de actividad para poder procesarlo. */
public final class ValidadorActividad {

    private ValidadorActividad() {
    }

    /** @throws EventoInvalidoException con la lista de campos que faltan o son incoherentes */
    public static ActividadPedido validar(ActividadPedido e) {
        if (e == null) {
            throw new EventoInvalidoException("evento vacío");
        }
        List<String> errores = new ArrayList<>();
        if (e.eventoId() == null) {
            errores.add("eventoId es obligatorio");
        }
        if (e.tipo() == null) {
            errores.add("tipo es obligatorio");
        }
        if (e.pedidoId() == null || e.pedidoId() <= 0) {
            errores.add("pedidoId debe ser positivo");
        }
        if (e.ocurridoEn() == null) {
            errores.add("ocurridoEn es obligatorio");
        }
        if (e.tipo() == TipoActividad.PEDIDO_CREADO && (e.items() == null || e.items().isEmpty())) {
            errores.add("un PEDIDO_CREADO debe traer items");
        }
        if (e.total() != null && e.total().signum() < 0) {
            errores.add("total no puede ser negativo");
        }
        if (!errores.isEmpty()) {
            throw new EventoInvalidoException(String.join("; ", errores));
        }
        return e;
    }
}
