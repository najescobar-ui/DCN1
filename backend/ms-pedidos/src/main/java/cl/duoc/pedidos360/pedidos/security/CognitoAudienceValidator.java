package cl.duoc.pedidos360.pedidos.security;

import java.util.List;

import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Cognito access tokens carry the app client in "client_id" instead of "aud"
 * (ID tokens do use "aud"). The token is accepted only if either one matches
 * an allowed client id.
 */
public class CognitoAudienceValidator implements OAuth2TokenValidator<Jwt> {

    private static final OAuth2Error INVALID_AUDIENCE = new OAuth2Error(OAuth2ErrorCodes.INVALID_TOKEN,
            "El token no fue emitido para esta aplicacion (audience/client_id invalido)", null);

    private final List<String> allowedClientIds;

    public CognitoAudienceValidator(List<String> allowedClientIds) {
        this.allowedClientIds = List.copyOf(allowedClientIds);
    }

    @Override
    public OAuth2TokenValidatorResult validate(Jwt jwt) {
        String clientId = jwt.getClaimAsString("client_id");
        List<String> audience = jwt.getAudience();
        boolean valid = (clientId != null && allowedClientIds.contains(clientId))
                || (audience != null && audience.stream().anyMatch(allowedClientIds::contains));
        return valid ? OAuth2TokenValidatorResult.success() : OAuth2TokenValidatorResult.failure(INVALID_AUDIENCE);
    }
}
