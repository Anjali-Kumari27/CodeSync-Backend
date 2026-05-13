package com.codesync.auth.config;

import com.codesync.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Spring Security configuration for the Auth Service.
 *
 * Supports:
 *  - Email / password authentication
 *  - Google OAuth login
 *  - Swagger access
 *  - Actuator endpoints
 *
 * JWT validation for downstream services
 * is handled at API Gateway.
 */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final UserRepository userRepository;
    private final OAuth2LoginSuccessHandler successHandler;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)

                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                .authorizeHttpRequests(auth -> auth

                        // Swagger UI
                        .requestMatchers(
                                "/swagger-ui/**",
                                "/swagger-ui.html",
                                "/v3/api-docs/**"
                        ).permitAll()

                        // Actuator
                        .requestMatchers("/actuator/**").permitAll()

                        // All auth endpoints — JWT validation is done at the API Gateway,
                        // so auth-service itself permits everything under /api/auth/**
                        .requestMatchers("/api/auth/**").permitAll()

                        // OAuth2
                        .requestMatchers("/oauth2/**", "/login/oauth2/**").permitAll()

                        // Remaining endpoints
                        .anyRequest().permitAll()
                )

                // Google OAuth
                .oauth2Login(oauth ->
                        oauth.successHandler(successHandler)
                )

                .build();
    }

    @Bean
    public UserDetailsService userDetailsService() {
        return username ->
                userRepository.findByEmail(username)
                        .orElseThrow(() ->
                                new UsernameNotFoundException(
                                        "User not found: " + username));
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider provider =
                new DaoAuthenticationProvider();

        provider.setUserDetailsService(userDetailsService());
        provider.setPasswordEncoder(passwordEncoder());

        return provider;
    }

    @Bean
    public AuthenticationManager authenticationManager(
            AuthenticationConfiguration config
    ) throws Exception {
        return config.getAuthenticationManager();
    }
}