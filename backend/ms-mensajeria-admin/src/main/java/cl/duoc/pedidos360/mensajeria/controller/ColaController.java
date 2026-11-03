package cl.duoc.pedidos360.mensajeria.controller;

import java.net.URI;
import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import cl.duoc.pedidos360.mensajeria.dto.CrearColaRequest;
import cl.duoc.pedidos360.mensajeria.dto.Nombres;
import cl.duoc.pedidos360.mensajeria.dto.Respuestas.ColaInfo;
import cl.duoc.pedidos360.mensajeria.dto.Respuestas.ReprocesoInfo;
import cl.duoc.pedidos360.mensajeria.service.RabbitAdminService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;

/**
 * Colas.
 * <pre>
 * GET    /api/mensajeria/queues                    lista con contadores
 * GET    /api/mensajeria/queues/{nombre}           detalle
 * POST   /api/mensajeria/queues                    crea (201)
 * DELETE /api/mensajeria/queues/{nombre}           elimina (204); ?soloSiVacia=false fuerza
 * DELETE /api/mensajeria/queues/{nombre}/messages  purga (200 con la cantidad)
 * POST   /api/mensajeria/queues/{nombre}/reprocess devuelve una DLQ a su cola de origen
 * </pre>
 */
@RestController
@RequestMapping("/api/mensajeria/queues")
public class ColaController {

    private final RabbitAdminService service;

    public ColaController(RabbitAdminService service) {
        this.service = service;
    }

    @GetMapping
    public List<ColaInfo> listar() {
        return service.listarColas();
    }

    @GetMapping("/{nombre}")
    public ColaInfo obtener(@PathVariable @Pattern(regexp = Nombres.PATRON_NOMBRE, message = Nombres.MENSAJE_NOMBRE) String nombre) {
        return service.obtenerCola(nombre);
    }

    @PostMapping
    public ResponseEntity<ColaInfo> crear(@Valid @RequestBody CrearColaRequest request) {
        ColaInfo creada = service.crearCola(request);
        return ResponseEntity.created(URI.create("/api/mensajeria/queues/" + creada.nombre())).body(creada);
    }

    @DeleteMapping("/{nombre}")
    public ResponseEntity<Void> eliminar(
            @PathVariable @Pattern(regexp = Nombres.PATRON_NOMBRE, message = Nombres.MENSAJE_NOMBRE) String nombre,
            @RequestParam(defaultValue = "true") boolean soloSiVacia) {
        service.eliminarCola(nombre, soloSiVacia);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{nombre}/messages")
    public Map<String, Object> purgar(
            @PathVariable @Pattern(regexp = Nombres.PATRON_NOMBRE, message = Nombres.MENSAJE_NOMBRE) String nombre) {
        return Map.of("cola", nombre, "descartados", service.purgarCola(nombre));
    }

    @PostMapping("/{nombre}/reprocess")
    public ReprocesoInfo reprocesar(
            @PathVariable @Pattern(regexp = Nombres.PATRON_NOMBRE, message = Nombres.MENSAJE_NOMBRE) String nombre) {
        return service.reprocesarDlq(nombre);
    }
}
