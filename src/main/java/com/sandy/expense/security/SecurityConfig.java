package com.sandy.expense.security;

import java.util.List;
import org.springframework.http.HttpMethod;
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
                        .requestMatchers("/api/auth/**")
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
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
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
        // Auth is via the Authorization: Bearer header, never cookies, so credentials stay off and
        // allowing any origin is safe. This covers the Vite dev server, same-origin production
        // (the SPA is served by this app), and any future host without a rebuild. Note that even
        // same-origin ES-module script fetches send an Origin header, so this must not be a fixed list.
        c.setAllowedOriginPatterns(List.of("*"));
        c.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        c.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        UrlBasedCorsConfigurationSource src = new UrlBasedCorsConfigurationSource();
        src.registerCorsConfiguration("/**", c);
        return src;
    }
}
