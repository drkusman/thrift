package ng.asuu.thrift.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Value("${thrift.cors.allowed-origins}")
    private String[] allowedOrigins;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of(allowedOrigins));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    /** Everything that actually needs auth and Spring Security's default no-store/no-cache headers -
     *  those headers are exactly right for a response with someone's savings balance in it, but wrong
     *  for the static frontend below (see apiFilterChain's counterpart), so /api and /actuator get their
     *  own chain rather than applying that policy blanket-wide. */
    @Bean
    @Order(1)
    public SecurityFilterChain apiFilterChain(HttpSecurity http, MemberUserDetailsService uds) throws Exception {
        http
            .securityMatcher("/api/**", "/actuator/**")
            .cors(c -> {})
            .csrf(csrf -> csrf.disable())
            .authorizeHttpRequests(a -> a
                .requestMatchers("/api/auth/login", "/api/auth/forgot-password", "/api/auth/reset-token-valid",
                        "/api/auth/reset-password", "/actuator/health").permitAll()
                .requestMatchers("/api/admin/**").hasAnyRole("ADMIN", "FIN_SEC")
                .anyRequest().authenticated())
            .exceptionHandling(e -> e
                .authenticationEntryPoint((req, res, ex) -> res.sendError(HttpStatus.UNAUTHORIZED.value()))
                .accessDeniedHandler((req, res, ex) -> res.sendError(HttpStatus.FORBIDDEN.value())))
            .logout(l -> l.logoutUrl("/api/auth/logout").deleteCookies("JSESSIONID")
                .logoutSuccessHandler((req, res, auth) -> res.setStatus(HttpStatus.OK.value())))
            .userDetailsService(uds);
        return http.build();
    }

    /** The statically-exported frontend (see SpaResourceConfig) - publicly loadable (it does its own
     *  auth gating client-side by calling /api/me and redirecting if that 401s), and deliberately
     *  without the API chain's cache-control headers: a service worker's own script-fetch algorithm
     *  rejects a response marked no-store with an opaque "unknown error", which broke PWA installability
     *  entirely until this was split out from the API's header policy. */
    @Bean
    @Order(2)
    public SecurityFilterChain staticFilterChain(HttpSecurity http) throws Exception {
        http
            .securityMatcher("/**")
            .csrf(csrf -> csrf.disable())
            .authorizeHttpRequests(a -> a.anyRequest().permitAll())
            .headers(headers -> headers.cacheControl(cache -> cache.disable()));
        return http.build();
    }
}
