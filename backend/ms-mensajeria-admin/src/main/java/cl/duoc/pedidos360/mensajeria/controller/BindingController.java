package cl.duoc.pedidos360.mensajeria.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import cl.duoc.pedidos360.mensajeria.dto.BindingRequest;
import cl.duoc.pedidos360.mensajeria.dto.Respuestas.BindingInfo;
import cl.duoc.pedidos360.mensajeria.service.RabbitAdminService;
import jakarta.validation.Valid;

/**
 * Bindings. Un binding se identifica por exchange + cola + routing key, así que la eliminación recibe
 * esos tres datos en el cuerpo (la routing key puede tener caracteres como # que no van bien en una URL).
 * <pre>
 * GET    /api/mensajeria/bindings  lista
 * POST   /api/mensajeria/bindings  crea (201)
 * DELETE /api/mensajeria/bindings  elimina (204)
 * </pre>
 */
@RestController
@RequestMapping("/api/mensajeria/bindings")
public class BindingController {

    private final RabbitAdminService service;

    public BindingController(RabbitAdminService service) {
        this.service = service;
    }

    @GetMapping
    public List<BindingInfo> listar() {
        return service.listarBindings();
    }

    @PostMapping
    public ResponseEntity<BindingInfo> crear(@Valid @RequestBody BindingRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.crearBinding(request));
    }

    @DeleteMapping
    public ResponseEntity<Void> eliminar(@Valid @RequestBody BindingRequest request) {
        service.eliminarBinding(request);
        return ResponseEntity.noContent().build();
    }
}
