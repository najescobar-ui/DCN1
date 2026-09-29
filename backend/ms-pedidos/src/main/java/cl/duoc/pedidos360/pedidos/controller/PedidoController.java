package cl.duoc.pedidos360.pedidos.controller;

import java.net.URI;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import cl.duoc.pedidos360.pedidos.dto.CambiarEstadoPagoRequest;
import cl.duoc.pedidos360.pedidos.dto.CambiarEstadoRequest;
import cl.duoc.pedidos360.pedidos.dto.CrearPedidoRequest;
import cl.duoc.pedidos360.pedidos.dto.PedidoResponse;
import cl.duoc.pedidos360.pedidos.service.PedidoService;
import cl.duoc.pedidos360.pedidos.service.Usuario;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/pedidos")
public class PedidoController {

    private final PedidoService service;

    public PedidoController(PedidoService service) {
        this.service = service;
    }

    /** ADMIN sees every order; CLIENTE only their own. */
    @GetMapping
    public List<PedidoResponse> listar(JwtAuthenticationToken auth) {
        return service.listar(Usuario.from(auth));
    }

    @GetMapping("/{id}")
    public PedidoResponse obtener(@PathVariable Long id, JwtAuthenticationToken auth) {
        return service.obtener(id, Usuario.from(auth));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('CLIENTE', 'ADMIN')")
    public ResponseEntity<PedidoResponse> crear(@Valid @RequestBody CrearPedidoRequest request,
            JwtAuthenticationToken auth) {
        PedidoResponse creado = service.crear(request, Usuario.from(auth));
        return ResponseEntity.created(URI.create("/api/pedidos/" + creado.id())).body(creado);
    }

    @PatchMapping("/{id}/estado")
    @PreAuthorize("hasRole('ADMIN')")
    public PedidoResponse cambiarEstado(@PathVariable Long id, @Valid @RequestBody CambiarEstadoRequest request) {
        return service.cambiarEstado(id, request.estado());
    }

    /** For transfers and cash: an admin confirms the payment manually. */
    @PatchMapping("/{id}/pago")
    @PreAuthorize("hasRole('ADMIN')")
    public PedidoResponse cambiarEstadoPago(@PathVariable Long id, @Valid @RequestBody CambiarEstadoPagoRequest request) {
        return service.cambiarEstadoPago(id, request.estadoPago());
    }

    @PostMapping("/{id}/cancelar")
    public PedidoResponse cancelar(@PathVariable Long id, JwtAuthenticationToken auth) {
        return service.cancelar(id, Usuario.from(auth));
    }
}
