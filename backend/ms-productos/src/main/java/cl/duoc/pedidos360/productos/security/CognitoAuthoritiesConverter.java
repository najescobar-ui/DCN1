package cl.duoc.pedidos360.productos.security;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;

/**
 * Maps a Cognito access token to Spring authorities:
 * "scope" claim -> SCOPE_xxx, "cognito:groups" claim -> ROLE_XXX.
 */
public class CognitoAuthoritiesConverter implements Converter<Jwt, Collection<GrantedAuthority>> {

    static final String GROUPS_CLAIM = "cognito:groups";

    private final JwtGrantedAuthoritiesConverter scopesConverter = new JwtGrantedAuthoritiesConverter();
    private final String defaultRole;

    public CognitoAuthoritiesConverter(String defaultRole) {
        this.defaultRole = defaultRole;
    }

    @Override
    public Collection<GrantedAuthority> convert(Jwt jwt) {
        Set<GrantedAuthority> authorities = new HashSet<>(scopesConverter.convert(jwt));
        List<String> groups = jwt.getClaimAsStringList(GROUPS_CLAIM);
        if (groups == null || groups.isEmpty()) {
            if (defaultRole != null && !defaultRole.isBlank()) {
                authorities.add(role(defaultRole));
            }
        } else {
            groups.forEach(group -> authorities.add(role(group)));
        }
        return authorities;
    }

    private static GrantedAuthority role(String name) {
        return new SimpleGrantedAuthority("ROLE_" + name.toUpperCase(Locale.ROOT));
    }
}
