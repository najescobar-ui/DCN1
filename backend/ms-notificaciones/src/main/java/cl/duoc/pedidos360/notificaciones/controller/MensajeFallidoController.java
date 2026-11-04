package cl.duoc.pedidos360.notificaciones.controller;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import cl.duoc.pedidos360.notificaciones.fallido.MensajeFallido;
import cl.duoc.pedidos360.notificaciones.fallido.MensajeFallidoService;

/** Mensajes no entregados que registraron los listeners de las DLQ. Solo para el administrador. */
@RestController
@RequestMapping("/api/mensajes-fallidos")
@PreAuthorize("hasRole('ADMIN')")
public class MensajeFallidoController {

    private final MensajeFallidoService service;

    public MensajeFallidoController(MensajeFallidoService service) {
        this.service = service;
    }

    @GetMapping
    public List<MensajeFallido> listar() {
        return service.listar();
    }

    @PostMapping("/{id}/reprocesar")
    public MensajeFallido reprocesar(@PathVariable Long id) {
        return service.reprocesar(id);
    }
}
