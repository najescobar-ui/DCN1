package cl.duoc.pedidos360.bff.dto;

import java.time.Instant;
import java.util.List;

/** What the backend understood from the access token (useful to demo claims, roles and scopes). */
public record UsuarioResponse(String sub, String username, String clientId, String issuer, List<String> roles,
        List<String> scopes, Instant emitidoEn, Instant expiraEn) {
}
