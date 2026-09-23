package io.github.diegofranciscog.inventory.config;

import java.util.Base64;
import java.util.List;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import com.nimbusds.jose.jwk.source.ImmutableSecret;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import io.github.diegofranciscog.inventory.security.TokenService;

/**
 * Seguridad deny-by-default: todo requiere JWT salvo login, health y la documentación OpenAPI. API sin estado (sin
 * cookies de sesión), por eso CSRF no aplica; CORS solo para los orígenes configurados por variable de entorno.
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    private static final int MIN_SECRET_BYTES = 32;

    @Bean
    @Order(1)
    SecurityFilterChain apiDocsChain(HttpSecurity http) throws Exception {
        http.securityMatcher("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs", "/v3/api-docs/**")
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .headers(headers -> headers.contentSecurityPolicy(csp -> csp.policyDirectives(
                        "default-src 'self'; img-src 'self' data:; style-src 'self' 'unsafe-inline'; "
                                + "script-src 'self' 'unsafe-inline'; frame-ancestors 'none'")));
        return http.build();
    }

    @Bean
    @Order(2)
    SecurityFilterChain apiChain(HttpSecurity http, JwtAuthenticationConverter jwtAuthenticationConverter) throws Exception {
        http.cors(Customizer.withDefaults())
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST, "/api/auth/login").permitAll()
                        .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                        .requestMatchers("/error").permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth -> oauth.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter)))
                .headers(headers -> headers
                        .contentSecurityPolicy(csp -> csp.policyDirectives("default-src 'none'; frame-ancestors 'none'"))
                        .referrerPolicy(referrer -> referrer.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER))
                        .frameOptions(frame -> frame.deny()));
        return http.build();
    }

    @Bean
    SecretKey jwtSigningKey(AppProperties properties) {
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(properties.security().jwtSecret().strip());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("JWT_SECRET debe estar en Base64 (genera uno con: openssl rand -base64 48)", e);
        }
        if (bytes.length < MIN_SECRET_BYTES) {
            throw new IllegalStateException("JWT_SECRET debe tener al menos 256 bits (32 bytes)");
        }
        return new SecretKeySpec(bytes, "HmacSHA256");
    }

    @Bean
    JwtDecoder jwtDecoder(SecretKey jwtSigningKey, AppProperties properties) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(jwtSigningKey).macAlgorithm(MacAlgorithm.HS256).build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(properties.security().jwtIssuer()));
        return decoder;
    }

    @Bean
    JwtEncoder jwtEncoder(SecretKey jwtSigningKey) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(jwtSigningKey));
    }

    @Bean
    JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter authorities = new JwtGrantedAuthoritiesConverter();
        authorities.setAuthoritiesClaimName(TokenService.CLAIM_ROLE);
        authorities.setAuthorityPrefix("ROLE_");
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authorities);
        return converter;
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(AppProperties properties) {
        List<String> origins = properties.security().corsAllowedOrigins().stream().map(String::strip).toList();
        if (origins.stream().anyMatch(origin -> origin.contains("*"))) {
            throw new IllegalStateException("CORS_ALLOWED_ORIGINS no admite comodines: lista los orígenes exactos");
        }
        CorsConfiguration cors = new CorsConfiguration();
        cors.setAllowedOrigins(origins);
        cors.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        cors.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        cors.setExposedHeaders(List.of("Content-Disposition", "Retry-After"));
        cors.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", cors);
        return source;
    }
}
