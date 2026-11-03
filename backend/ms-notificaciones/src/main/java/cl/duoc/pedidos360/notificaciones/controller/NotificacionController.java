package cl.duoc.pedidos360.notificaciones.controller;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import cl.duoc.pedidos360.notificaciones.auditoria.AuditoriaService;
import cl.duoc.pedidos360.notificaciones.auditoria.RegistroAuditoria;
import cl.duoc.pedidos360.notificaciones.comprobante.Comprobante;
import cl.duoc.pedidos360.notificaciones.comprobante.ComprobanteService;
import cl.duoc.pedidos360.notificaciones.dto.Usuario;
import cl.duoc.pedidos360.notificaciones.notificacion.Notificacion;
import cl.duoc.pedidos360.notificaciones.notificacion.NotificacionService;

/** Consulta de lo que generaron los consumidores. El CLIENTE ve lo suyo; el ADMIN ve todo. */
@RestController
@RequestMapping("/api")
public class NotificacionController {

    private final NotificacionService notificaciones;
    private final ComprobanteService comprobantes;
    private final AuditoriaService auditoria;

    public NotificacionController(NotificacionService notificaciones, ComprobanteService comprobantes,
            AuditoriaService auditoria) {
        this.notificaciones = notificaciones;
        this.comprobantes = comprobantes;
        this.auditoria = auditoria;
    }

    @GetMapping("/notificaciones")
    public List<Notificacion> notificaciones(JwtAuthenticationToken auth) {
        Usuario u = Usuario.from(auth);
        return notificaciones.listar(u.id(), u.esAdmin());
    }

    @GetMapping("/comprobantes")
    public List<Comprobante> comprobantes(JwtAuthenticationToken auth) {
        Usuario u = Usuario.from(auth);
        return comprobantes.listar(u.id(), u.esAdmin());
    }

    @GetMapping("/auditoria")
    @PreAuthorize("hasRole('ADMIN')")
    public List<RegistroAuditoria> auditoria() {
        return auditoria.listar();
    }
}
