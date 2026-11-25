package cl.duoc.pedidos360.analitica.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import cl.duoc.pedidos360.analitica.dto.CambiarConsumidoresRequest;
import cl.duoc.pedidos360.analitica.dto.ConsumidorInfo;
import cl.duoc.pedidos360.analitica.service.ConsumidoresService;
import jakarta.validation.Valid;

/**
 * Consumidores de esta instancia (exige scope streaming.admin y grupo ADMIN).
 * <pre>
 * GET /api/analitica/consumidores        grupos, consumidores activos y particiones de cada uno
 * PUT /api/analitica/consumidores/{id}   {"consumidores": 0..3}: detiene, achica o agranda el grupo
 * </pre>
 */
@RestController
@RequestMapping("/api/analitica/consumidores")
public class ConsumidorController {

    private final ConsumidoresService service;

    public ConsumidorController(ConsumidoresService service) {
        this.service = service;
    }

    @GetMapping
    public List<ConsumidorInfo> listar() {
        return service.listar();
    }

    @PutMapping("/{id}")
    public ConsumidorInfo cambiar(@PathVariable String id, @Valid @RequestBody CambiarConsumidoresRequest request) {
        return service.cambiarConcurrencia(id, request.consumidores());
    }
}
