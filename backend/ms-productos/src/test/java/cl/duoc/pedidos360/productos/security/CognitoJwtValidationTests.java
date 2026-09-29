package cl.duoc.pedidos360.productos.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

class CognitoJwtValidationTests {

    private final CognitoAudienceValidator audienceValidator = new CognitoAudienceValidator(List.of("app-client-123"));
    private final CognitoAuthoritiesConverter authoritiesConverter = new CognitoAuthoritiesConverter("CLIENTE");

    @Test
    void aceptaAccessTokenConClientIdPermitido() {
        assertThat(audienceValidator.validate(token("client_id", "app-client-123")).hasErrors()).isFalse();
    }

    @Test
    void rechazaTokenDeOtroCliente() {
        assertThat(audienceValidator.validate(token("client_id", "otra-app")).hasErrors()).isTrue();
    }

    @Test
    void aceptaIdTokenPorAudience() {
        assertThat(audienceValidator.validate(token("aud", List.of("app-client-123"))).hasErrors()).isFalse();
    }

    @Test
    void mapeaGruposYScopes() {
        Jwt jwt = Jwt.withTokenValue("t").header("alg", "RS256")
                .claim("scope", "pedidos360/productos.read pedidos360/pedidos.write")
                .claim("cognito:groups", List.of("admin"))
                .build();
        assertThat(authoritiesConverter.convert(jwt)).extracting(GrantedAuthority::getAuthority)
                .containsExactlyInAnyOrder("SCOPE_pedidos360/productos.read", "SCOPE_pedidos360/pedidos.write", "ROLE_ADMIN");
    }

    @Test
    void usuarioSinGrupoRecibeRolPorDefecto() {
        Jwt jwt = Jwt.withTokenValue("t").header("alg", "RS256").claim("sub", "u1").build();
        assertThat(authoritiesConverter.convert(jwt)).extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_CLIENTE");
    }

    private static Jwt token(String claim, Object value) {
        return Jwt.withTokenValue("t").header("alg", "RS256").claim(claim, value)
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60)).build();
    }
}
