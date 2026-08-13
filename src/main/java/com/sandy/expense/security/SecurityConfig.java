package com.sandy.expense.security;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableMethodSecurity // enables @PreAuthorize on service/controller methods
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtFilter;

    public SecurityConfig(JwtAuthenticationFilter jwtFilter) {
        this.jwtFilter = jwtFilter;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http.csrf(AbstractHttpConfigurer::disable) // stateless JWT API, no cookies -> no CSRF
                .cors(cors -> {}) // use the CorsConfigurationSource bean below
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // Only login and register are public. /api/auth/me must be authenticated:
                        // as part of the permitAll group it was reached with a null principal and
                        // blew up with a 500 — and it's the call the SPA makes on every page load.
                        .requestMatchers("/api/auth/login", "/api/auth/register")
                        .permitAll()
                        .requestMatchers("/actuator/health", "/error")
                        .permitAll()
                        // The SPA shell + its static assets are public (just JS/HTML); the data
                        // API under /api/** stays protected. The React app calls /api/auth/me and
                        // redirects to /login itself when unauthenticated.
                        .requestMatchers(
                                HttpMethod.GET,
                                "/",
                                "/index.html",
                                "/login",
                                "/requests/**",
                                "/assets/**",
                                "/favicon.ico",
                                "/vite.svg")
                        .permitAll()
                        .anyRequest()
                        .authenticated())
                // Without these, Spring Security falls back to Http403ForbiddenEntryPoint (because
                // httpBasic and formLogin are disabled): a missing or expired token produced a 403
                // with an empty body. That's the wrong status — the client should be told to
                // re-authenticate, not that it's forbidden — and the SPA keys its "session expired,
                // go to login" handling off 401, so it never fired. These also keep the
                // {code, message, timestamp} envelope intact for failures that happen in the filter
                // chain, before @RestControllerAdvice can see them.
                .exceptionHandling(ex -> ex.authenticationEntryPoint(
                                (req, res, e) -> writeError(res, 401, "UNAUTHORIZED",
                                        "Authentication required — sign in again"))
                        .accessDeniedHandler(
                                (req, res, e) -> writeError(res, 403, "FORBIDDEN",
                                        "You do not have permission to perform this action")))
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    /** Same envelope shape as GlobalExceptionHandler, written directly from the filter chain. */
    private static void writeError(HttpServletResponse res, int status, String code, String message)
            throws IOException {
        res.setStatus(status);
        res.setContentType(MediaType.APPLICATION_JSON_VALUE);
        res.setCharacterEncoding(StandardCharsets.UTF_8.name());
        res.getWriter()
                .write("{\"code\":\"%s\",\"message\":\"%s\",\"timestamp\":\"%s\"}"
                        .formatted(code, message, Instant.now()));
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration cfg)
            throws Exception {
        // Uses the auto-configured DaoAuthenticationProvider (AppUserDetailsService + BCrypt).
        return cfg.getAuthenticationManager();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration c = new CorsConfiguration();
        // Auth is a Bearer header, never a cookie, so credentials stay off and a wildcard origin
        // can't be used to ride an ambient session — a caller still needs a token they obtained
        // some other way. Kept permissive so the demo works from any host it's deployed to.
        //
        // Why this matters even though the SPA is same-origin: Vite emits its bundles as
        // <script type="module" crossorigin>, and the crossorigin attribute makes the browser send
        // an Origin header and apply CORS rules to those requests. A fixed localhost allow-list
        // therefore had the API reject the app's own JS/CSS in production, blanking the page.
        c.setAllowedOriginPatterns(List.of("*"));
        c.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        c.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        UrlBasedCorsConfigurationSource src = new UrlBasedCorsConfigurationSource();
        src.registerCorsConfiguration("/**", c);
        return src;
    }
}
