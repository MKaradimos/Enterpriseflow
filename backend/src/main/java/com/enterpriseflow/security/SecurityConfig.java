package com.enterpriseflow.security;

import java.util.Set;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.util.matcher.RequestMatcher;

/**
 * Stateless JWT API. CSRF only on the cookie-based auth endpoints, everything else needs a token.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    static final String AUTH_BASE = "/api/v1/auth";

    /**
     * Only these endpoints authenticate with the refresh-token cookie, so only they can be targeted by CSRF.
     * Everything else authenticates with the Authorization header, which a cross-site page cannot set.
     * Login is not listed: it carries no cookie credential and requires a JSON body, which a cross-site
     * form cannot send without a CORS preflight (and that keeps it usable from Swagger UI).
     */
    private static final Set<String> CSRF_PROTECTED_PATHS = Set.of(AUTH_BASE + "/refresh", AUTH_BASE + "/logout");

    private static final String[] PUBLIC_GET_ENDPOINTS = {
            "/actuator/health/**",
            "/actuator/info",
            "/v3/api-docs/**",
            "/swagger-ui/**",
            "/swagger-ui.html",
            AUTH_BASE + "/csrf"
    };

    private static final String[] PUBLIC_POST_ENDPOINTS = {
            AUTH_BASE + "/register",
            AUTH_BASE + "/login",
            AUTH_BASE + "/refresh",
            AUTH_BASE + "/logout"
    };

    private final ProblemDetailSecurityHandler problemDetailHandler;

    public SecurityConfig(ProblemDetailSecurityHandler problemDetailHandler) {
        this.problemDetailHandler = problemDetailHandler;
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        RequestMatcher csrfProtected = request ->
                HttpMethod.POST.matches(request.getMethod()) && CSRF_PROTECTED_PATHS.contains(request.getRequestURI());

        // Double-submit pattern: the token lives in an HttpOnly cookie and is also returned by GET /auth/csrf;
        // the SPA echoes it in the X-XSRF-TOKEN header. A cross-site attacker can trigger the cookie but
        // cannot read the token to put in the header.
        CookieCsrfTokenRepository csrfRepository = new CookieCsrfTokenRepository();
        csrfRepository.setCookieCustomizer(cookie -> cookie.sameSite("Strict").path("/"));

        http
                .cors(Customizer.withDefaults())
                // Stateless API: no HTTP session; every request carries its own bearer token.
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .csrf(csrf -> csrf
                        .csrfTokenRepository(csrfRepository)
                        // Plain (non-XOR) handler: the SPA sends back exactly the value it received.
                        .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler())
                        .requireCsrfProtectionMatcher(csrfProtected))
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.GET, PUBLIC_GET_ENDPOINTS).permitAll()
                        .requestMatchers(HttpMethod.POST, PUBLIC_POST_ENDPOINTS).permitAll()
                        // Deny-by-default: anything not explicitly opened requires a valid access token.
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter()))
                        .authenticationEntryPoint(problemDetailHandler)
                        .accessDeniedHandler(problemDetailHandler))
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(problemDetailHandler)
                        .accessDeniedHandler(problemDetailHandler));

        return http.build();
    }

    /** Maps the "roles" claim (["ADMIN"]) to Spring authorities (ROLE_ADMIN) so hasRole() works. */
    private JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter authorities = new JwtGrantedAuthoritiesConverter();
        authorities.setAuthoritiesClaimName(JwtTokenService.ROLES_CLAIM);
        authorities.setAuthorityPrefix("ROLE_");

        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authorities);
        return converter;
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        // Strength 12 ≈ 250 ms per hash: slow enough to resist offline brute force, fast enough for login.
        return new BCryptPasswordEncoder(12);
    }

    /** Built by Spring from our UserDetailsService and PasswordEncoder beans (DaoAuthenticationProvider). */
    @Bean
    AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }
}
