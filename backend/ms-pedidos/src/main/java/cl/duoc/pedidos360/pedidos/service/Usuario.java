package cl.duoc.pedidos360.pedidos.service;

import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/** Authenticated caller, taken from the validated Cognito access token. */
public record Usuario(String id, String username, boolean esAdmin) {

    public static Usuario from(JwtAuthenticationToken auth) {
        boolean admin = auth.getAuthorities().stream().anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
        String username = auth.getToken().getClaimAsString("username");
        return new Usuario(auth.getToken().getSubject(), username, admin);
    }
}
