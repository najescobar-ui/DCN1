package cl.duoc.pedidos360.mensajeria.controller;

import java.net.URI;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import cl.duoc.pedidos360.mensajeria.dto.CrearExchangeRequest;
import cl.duoc.pedidos360.mensajeria.dto.Nombres;
import cl.duoc.pedidos360.mensajeria.dto.Respuestas.ExchangeInfo;
import cl.duoc.pedidos360.mensajeria.service.RabbitAdminService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;

/**
 * Exchanges.
 * <pre>
 * GET    /api/mensajeria/exchanges           lista
 * GET    /api/mensajeria/exchanges/{nombre}  detalle
 * POST   /api/mensajeria/exchanges           crea (201)
 * DELETE /api/mensajeria/exchanges/{nombre}  elimina (204)
 * </pre>
 */
@RestController
@RequestMapping("/api/mensajeria/exchanges")
public class ExchangeController {

    private final RabbitAdminService service;

    public ExchangeController(RabbitAdminService service) {
        this.service = service;
    }

    @GetMapping
    public List<ExchangeInfo> listar() {
        return service.listarExchanges();
    }

    @GetMapping("/{nombre}")
    public ExchangeInfo obtener(@PathVariable @Pattern(regexp = Nombres.PATRON_NOMBRE, message = Nombres.MENSAJE_NOMBRE) String nombre) {
        return service.obtenerExchange(nombre);
    }

    @PostMapping
    public ResponseEntity<ExchangeInfo> crear(@Valid @RequestBody CrearExchangeRequest request) {
        ExchangeInfo creado = service.crearExchange(request);
        return ResponseEntity.created(URI.create("/api/mensajeria/exchanges/" + creado.nombre())).body(creado);
    }

    @DeleteMapping("/{nombre}")
    public ResponseEntity<Void> eliminar(
            @PathVariable @Pattern(regexp = Nombres.PATRON_NOMBRE, message = Nombres.MENSAJE_NOMBRE) String nombre) {
        service.eliminarExchange(nombre);
        return ResponseEntity.noContent().build();
    }
}
