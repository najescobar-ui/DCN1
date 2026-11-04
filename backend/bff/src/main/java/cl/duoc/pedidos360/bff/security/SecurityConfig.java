package cl.duoc.pedidos360.bff.security;

import jakarta.servlet.DispatcherType;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, JwtAuthenticationConverter jwtAuthenticationConverter,
            JsonSecurityErrorHandler errorHandler) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health/**").permitAll()
                        // Respuestas de error que Spring arma en /error (si no, saldrían como 403).
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        // Retorno del navegador desde Webpay: no trae token; ms-pedidos valida la transacción.
                        .requestMatchers("/api/pagos/webpay/retorno").permitAll()
                        .requestMatchers("/api/bff/me").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/bff/**").hasAuthority("SCOPE_pedidos360/pedidos.read")
                        // Autorización por rol: la administración es solo para ADMIN.
                        .requestMatchers("/api/mensajeria/**", "/api/mensajes-fallidos/**", "/api/auditoria")
                                .hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/productos/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/productos/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/productos/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PATCH, "/api/pedidos/*/estado", "/api/pedidos/*/pago")
                                .hasRole("ADMIN")
                        // El resto (catálogo, pedidos propios, notificaciones propias) requiere un token válido;
                        // el microservicio aplica además su scope y las reglas del dueño del recurso.
                        .anyRequest().authenticated())
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
