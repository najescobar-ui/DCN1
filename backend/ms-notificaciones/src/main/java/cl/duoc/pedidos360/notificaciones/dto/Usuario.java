package cl.duoc.pedidos360.notificaciones.dto;

import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/** Usuario autenticado, tomado del access token de Cognito ya validado. */
public record Usuario(String id, boolean esAdmin) {

    public static Usuario from(JwtAuthenticationToken auth) {
        boolean admin = auth.getAuthorities().stream().anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
        return new Usuario(auth.getToken().getSubject(), admin);
    }
}
