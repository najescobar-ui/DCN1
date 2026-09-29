package cl.duoc.pedidos360.pedidos.security;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Cognito settings used to validate incoming access tokens.
 *
 * @param issuerUri   user pool issuer, e.g. https://cognito-idp.us-east-1.amazonaws.com/us-east-1_XXXX
 * @param clientIds   app client ids allowed to call this API (Cognito's "audience" for access tokens)
 * @param defaultRole role granted when the user belongs to no Cognito group (self sign-up users)
 */
@ConfigurationProperties("app.security.cognito")
public record CognitoProperties(String issuerUri, List<String> clientIds, String defaultRole) {
}
