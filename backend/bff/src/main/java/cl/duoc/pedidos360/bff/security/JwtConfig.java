package cl.duoc.pedidos360.bff.security;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;

@Configuration
@EnableConfigurationProperties(CognitoProperties.class)
public class JwtConfig {

    /**
     * Verifies the RS256 signature against the user pool JWKS (fetched lazily and cached),
     * then checks exp/nbf, issuer, client id and that it is an access token.
     */
    @Bean
    JwtDecoder jwtDecoder(CognitoProperties cognito) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder
                .withJwkSetUri(cognito.issuerUri() + "/.well-known/jwks.json")
                .jwsAlgorithm(SignatureAlgorithm.RS256)
                .build();
        OAuth2TokenValidator<Jwt> validator = new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(cognito.issuerUri()),
                new CognitoAudienceValidator(cognito.clientIds()),
                new JwtClaimValidator<String>("token_use", "access"::equals));
        decoder.setJwtValidator(validator);
        return decoder;
    }

    @Bean
    JwtAuthenticationConverter jwtAuthenticationConverter(CognitoProperties cognito) {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(new CognitoAuthoritiesConverter(cognito.defaultRole()));
        return converter;
    }
}
