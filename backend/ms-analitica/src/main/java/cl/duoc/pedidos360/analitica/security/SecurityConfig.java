package cl.duoc.pedidos360.analitica.security;

import jakarta.servlet.DispatcherType;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authorization.AuthorizationManagers;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.authorization.AuthorityAuthorizationManager;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Reglas de acceso de ms-analitica:
 * <ul>
 * <li>Control de consumidores: scope pedidos360/streaming.admin (la app puede pedirlo) y grupo ADMIN (el usuario
 * puede ejercerlo).</li>
 * <li>Tablero de métricas: grupo ADMIN y scope de lectura de pedidos.</li>
 * <li>Seguimiento: scope de lectura de pedidos; el controlador filtra por dueño.</li>
 * </ul>
 */
@Configuration
public class SecurityConfig {

    static final String SCOPE_ADMIN = "SCOPE_pedidos360/streaming.admin";
    static final String SCOPE_LECTURA = "SCOPE_pedidos360/pedidos.read";

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, JwtAuthenticationConverter jwtAuthenticationConverter,
            JsonSecurityErrorHandler errorHandler) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health/**").permitAll()
                        // Respuestas de error que Spring arma en /error (si no, un 404 saldría como 403).
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .requestMatchers("/api/analitica/consumidores/**").access(AuthorizationManagers.allOf(
                                AuthorityAuthorizationManager.hasAuthority(SCOPE_ADMIN),
                                AuthorityAuthorizationManager.hasRole("ADMIN")))
                        .requestMatchers("/api/analitica/metricas").access(AuthorizationManagers.allOf(
                                AuthorityAuthorizationManager.hasAuthority(SCOPE_LECTURA),
                                AuthorityAuthorizationManager.hasRole("ADMIN")))
                        .requestMatchers("/api/analitica/seguimiento/**").hasAuthority(SCOPE_LECTURA)
                        .anyRequest().denyAll())
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter))
                        .authenticationEntryPoint(errorHandler)
                        .accessDeniedHandler(errorHandler))
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(errorHandler)
                        .accessDeniedHandler(errorHandler));
        return http.build();
    }
}
