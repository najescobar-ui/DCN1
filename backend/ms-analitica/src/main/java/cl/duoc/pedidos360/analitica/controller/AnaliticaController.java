package cl.duoc.pedidos360.analitica.controller;

import java.util.List;
import java.util.Map;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import cl.duoc.pedidos360.analitica.exception.RecursoNoEncontradoException;
import cl.duoc.pedidos360.analitica.proyeccion.AgregadorVentas;
import cl.duoc.pedidos360.analitica.proyeccion.SeguimientoPedidos;
import cl.duoc.pedidos360.analitica.proyeccion.TableroVentas;

/**
 * Lo que producen los consumidores, para el frontend (que lo consulta cada pocos segundos).
 * <pre>
 * GET /api/analitica/metricas              tablero de ventas de la última hora (ADMIN)
 * GET /api/analitica/seguimiento           pedidos recientes: los propios, o todos si es ADMIN
 * GET /api/analitica/seguimiento/{pedido}  línea de tiempo de un pedido (dueño o ADMIN)
 * </pre>
 */
@RestController
@RequestMapping("/api/analitica")
public class AnaliticaController {

    private static final int RECIENTES = 20;

    private final TableroVentas tablero;
    private final SeguimientoPedidos seguimiento;
    private final AgregadorVentas agregador;

    public AnaliticaController(TableroVentas tablero, SeguimientoPedidos seguimiento, AgregadorVentas agregador) {
        this.tablero = tablero;
        this.seguimiento = seguimiento;
        this.agregador = agregador;
    }

    @GetMapping("/metricas")
    public Map<String, Object> metricas() {
        return Map.of("resumen", tablero.resumen(), "eventosContados", agregador.eventosContados(),
                "pedidosEnSeguimiento", seguimiento.cantidad());
    }

    @GetMapping("/seguimiento")
    public List<SeguimientoPedidos.Vista> recientes(JwtAuthenticationToken auth) {
        return seguimiento.recientes(esAdmin(auth) ? null : auth.getToken().getSubject(), RECIENTES);
    }

    @GetMapping("/seguimiento/{pedidoId}")
    public SeguimientoPedidos.Vista uno(@PathVariable Long pedidoId, JwtAuthenticationToken auth) {
        SeguimientoPedidos.Vista vista = seguimiento.de(pedidoId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Aún no hay eventos del pedido " + pedidoId));
        if (!esAdmin(auth) && !auth.getToken().getSubject().equals(vista.clienteId())) {
            throw new AccessDeniedException("El pedido " + pedidoId + " no es tuyo");
        }
        return vista;
    }

    private static boolean esAdmin(JwtAuthenticationToken auth) {
        return auth.getAuthorities().stream().anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
    }
}
