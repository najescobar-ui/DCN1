package cl.duoc.pedidos360.bff.service;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;

import cl.duoc.pedidos360.bff.client.BackendClient;
import cl.duoc.pedidos360.bff.dto.PedidoDto;
import cl.duoc.pedidos360.bff.dto.ProductoDto;
import cl.duoc.pedidos360.bff.dto.ResumenResponse;
import cl.duoc.pedidos360.bff.dto.UsuarioResponse;

@Service
public class ResumenService {

    private final BackendClient backend;

    public ResumenService(BackendClient backend) {
        this.backend = backend;
    }

    public UsuarioResponse usuario(JwtAuthenticationToken auth) {
        Jwt jwt = auth.getToken();
        List<String> authorities = auth.getAuthorities().stream().map(GrantedAuthority::getAuthority).sorted().toList();
        return new UsuarioResponse(jwt.getSubject(), jwt.getClaimAsString("username"),
                jwt.getClaimAsString("client_id"), String.valueOf(jwt.getIssuer()),
                strip(authorities, "ROLE_"), strip(authorities, "SCOPE_"), jwt.getIssuedAt(), jwt.getExpiresAt());
    }

    /** Aggregates both microservices into a single call for the dashboard. */
    public ResumenResponse resumen(JwtAuthenticationToken auth) {
        List<ProductoDto> productos = backend.productos();
        List<PedidoDto> pedidos = backend.pedidos();

        Map<String, Long> porEstado = pedidos.stream()
                .collect(Collectors.groupingBy(PedidoDto::estado, TreeMap::new, Collectors.counting()));
        BigDecimal monto = pedidos.stream()
                .filter(p -> !"CANCELADO".equals(p.estado()))
                .map(PedidoDto::total)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        List<PedidoDto> ultimos = pedidos.stream()
                .sorted(Comparator.comparing(PedidoDto::creadoEn, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(5)
                .toList();
        List<ProductoDto> destacados = productos.stream().limit(3).toList();

        return new ResumenResponse(usuario(auth), productos.size(), pedidos.size(), monto, porEstado, ultimos,
                destacados);
    }

    private static List<String> strip(List<String> authorities, String prefix) {
        return authorities.stream().filter(a -> a.startsWith(prefix)).map(a -> a.substring(prefix.length())).toList();
    }
}
