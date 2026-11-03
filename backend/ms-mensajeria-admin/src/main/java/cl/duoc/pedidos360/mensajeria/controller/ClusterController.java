package cl.duoc.pedidos360.mensajeria.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import cl.duoc.pedidos360.mensajeria.dto.PublicarMensajeRequest;
import cl.duoc.pedidos360.mensajeria.dto.Respuestas.ClusterInfo;
import cl.duoc.pedidos360.mensajeria.dto.Respuestas.PublicacionInfo;
import cl.duoc.pedidos360.mensajeria.service.RabbitAdminService;
import jakarta.validation.Valid;

/**
 * <pre>
 * GET  /api/mensajeria/cluster   nombre, versión y nodos del clúster
 * POST /api/mensajeria/messages  publica un mensaje de prueba (202)
 * </pre>
 */
@RestController
@RequestMapping("/api/mensajeria")
public class ClusterController {

    private final RabbitAdminService service;

    public ClusterController(RabbitAdminService service) {
        this.service = service;
    }

    @GetMapping("/cluster")
    public ClusterInfo cluster() {
        return service.cluster();
    }

    @PostMapping("/messages")
    public ResponseEntity<PublicacionInfo> publicar(@Valid @RequestBody PublicarMensajeRequest request) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(service.publicar(request));
    }
}
