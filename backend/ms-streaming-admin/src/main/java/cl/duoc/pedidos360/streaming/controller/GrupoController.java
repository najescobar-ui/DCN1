package cl.duoc.pedidos360.streaming.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import cl.duoc.pedidos360.streaming.dto.Nombres;
import cl.duoc.pedidos360.streaming.dto.ResetOffsetsRequest;
import cl.duoc.pedidos360.streaming.dto.Respuestas.GrupoDetalle;
import cl.duoc.pedidos360.streaming.dto.Respuestas.GrupoResumen;
import cl.duoc.pedidos360.streaming.dto.Respuestas.ResetResultado;
import cl.duoc.pedidos360.streaming.service.KafkaAdminService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;

/**
 * Consumer groups.
 * <pre>
 * GET  /api/streaming/groups               estado, miembros, tópicos y lag total de cada grupo
 * GET  /api/streaming/groups/{id}          miembros con sus particiones y lag por partición
 * POST /api/streaming/groups/{id}/offsets  mueve los offsets (INICIO, FIN o FECHA); el grupo debe estar detenido
 * </pre>
 */
@RestController
@RequestMapping("/api/streaming/groups")
public class GrupoController {

    private final KafkaAdminService service;

    public GrupoController(KafkaAdminService service) {
        this.service = service;
    }

    @GetMapping
    public List<GrupoResumen> listar() {
        return service.listarGrupos();
    }

    @GetMapping("/{id}")
    public GrupoDetalle obtener(
            @PathVariable @Pattern(regexp = Nombres.PATRON_GRUPO, message = Nombres.MENSAJE_GRUPO) String id) {
        return service.obtenerGrupo(id);
    }

    @PostMapping("/{id}/offsets")
    public ResetResultado resetear(
            @PathVariable @Pattern(regexp = Nombres.PATRON_GRUPO, message = Nombres.MENSAJE_GRUPO) String id,
            @Valid @RequestBody ResetOffsetsRequest request) {
        return service.resetearOffsets(id, request);
    }
}
