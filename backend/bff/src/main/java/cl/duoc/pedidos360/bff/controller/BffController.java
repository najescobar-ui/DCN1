package cl.duoc.pedidos360.bff.controller;

import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import cl.duoc.pedidos360.bff.dto.ResumenResponse;
import cl.duoc.pedidos360.bff.dto.UsuarioResponse;
import cl.duoc.pedidos360.bff.service.ResumenService;

@RestController
@RequestMapping("/api/bff")
public class BffController {

    private final ResumenService service;

    public BffController(ResumenService service) {
        this.service = service;
    }

    @GetMapping("/me")
    public UsuarioResponse me(JwtAuthenticationToken auth) {
        return service.usuario(auth);
    }

    @GetMapping("/resumen")
    public ResumenResponse resumen(JwtAuthenticationToken auth) {
        return service.resumen(auth);
    }
}
