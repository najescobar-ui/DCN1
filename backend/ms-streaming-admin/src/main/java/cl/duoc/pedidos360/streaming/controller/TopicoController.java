package cl.duoc.pedidos360.streaming.controller;

import java.net.URI;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import cl.duoc.pedidos360.streaming.dto.AumentarParticionesRequest;
import cl.duoc.pedidos360.streaming.dto.CrearTopicoRequest;
import cl.duoc.pedidos360.streaming.dto.ModificarTopicoRequest;
import cl.duoc.pedidos360.streaming.dto.Nombres;
import cl.duoc.pedidos360.streaming.dto.Respuestas.TopicoDetalle;
import cl.duoc.pedidos360.streaming.dto.Respuestas.TopicoResumen;
import cl.duoc.pedidos360.streaming.service.KafkaAdminService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;

/**
 * Tópicos.
 * <pre>
 * GET    /api/streaming/topics                       lista con particiones, réplicas, ISR, retención y mensajes
 * GET    /api/streaming/topics/{nombre}              detalle: líder, réplicas, ISR y offsets por partición; configuración
 * POST   /api/streaming/topics                       crea (201)
 * PATCH  /api/streaming/topics/{nombre}              cambia retención, cleanup policy o min.insync.replicas
 * POST   /api/streaming/topics/{nombre}/partitions   aumenta las particiones
 * DELETE /api/streaming/topics/{nombre}              elimina (204); los del sistema responden 409
 * </pre>
 */
@RestController
@RequestMapping("/api/streaming/topics")
public class TopicoController {

    private final KafkaAdminService service;

    public TopicoController(KafkaAdminService service) {
        this.service = service;
    }

    @GetMapping
    public List<TopicoResumen> listar() {
        return service.listarTopicos();
    }

    @GetMapping("/{nombre}")
    public TopicoDetalle obtener(@PathVariable String nombre) {
        return service.obtenerTopico(nombre);
    }

    @PostMapping
    public ResponseEntity<TopicoDetalle> crear(@Valid @RequestBody CrearTopicoRequest request) {
        TopicoDetalle creado = service.crearTopico(request);
        return ResponseEntity.created(URI.create("/api/streaming/topics/" + creado.nombre())).body(creado);
    }

    @PatchMapping("/{nombre}")
    public TopicoDetalle modificar(@PathVariable String nombre, @Valid @RequestBody ModificarTopicoRequest request) {
        return service.modificarTopico(nombre, request);
    }

    @PostMapping("/{nombre}/partitions")
    public TopicoDetalle aumentarParticiones(@PathVariable String nombre,
            @Valid @RequestBody AumentarParticionesRequest request) {
        return service.aumentarParticiones(nombre, request.total());
    }

    @DeleteMapping("/{nombre}")
    public ResponseEntity<Void> eliminar(
            @PathVariable @Pattern(regexp = Nombres.PATRON_TOPICO, message = Nombres.MENSAJE_TOPICO) String nombre) {
        service.eliminarTopico(nombre);
        return ResponseEntity.noContent().build();
    }
}
