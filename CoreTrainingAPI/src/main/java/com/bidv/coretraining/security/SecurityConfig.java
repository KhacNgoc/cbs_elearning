package com.bidv.coretraining.security;

import com.bidv.coretraining.logging.RequestLoggingFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final AdminRequestAuthorization adminAuthorization;
    @org.springframework.beans.factory.annotation.Value("${app.jwt.cookie-secure:false}")
    private boolean csrfCookieSecure;
    @org.springframework.beans.factory.annotation.Value("${app.jwt.cookie-same-site:Lax}")
    private String csrfCookieSameSite;
    private final JwtTokenProvider tokenProvider;
    private final UserDetailsServiceImpl userDetailsService;
    private final JwtCookieService jwtCookieService;

    @org.springframework.beans.factory.annotation.Value("${app.cors.allowed-origins}")
    private java.util.List<String> allowedOrigins;

    public SecurityConfig(JwtTokenProvider tokenProvider, UserDetailsServiceImpl userDetailsService,
            JwtCookieService jwtCookieService,
            AdminRequestAuthorization adminAuthorization) {
        this.tokenProvider = tokenProvider;
        this.userDetailsService = userDetailsService;
        this.jwtCookieService = jwtCookieService;
        this.adminAuthorization = adminAuthorization;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authenticationConfiguration)
            throws Exception {
        return authenticationConfiguration.getAuthenticationManager();
    }

    /** Shared with AuthController, which rotates the token on login and logout. */
    @Bean
    public org.springframework.security.web.csrf.CsrfTokenRepository csrfTokenRepository() {
        var csrfCookies = org.springframework.security.web.csrf.CookieCsrfTokenRepository.withHttpOnlyFalse();
        csrfCookies.setCookieCustomizer(cookie -> cookie.secure(csrfCookieSecure).sameSite(csrfCookieSameSite));
        return csrfCookies;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        JwtAuthenticationFilter jwtAuthenticationFilter = new JwtAuthenticationFilter(
                tokenProvider, userDetailsService, jwtCookieService);
        RequestLoggingFilter requestLoggingFilter = new RequestLoggingFilter();

        http.headers(headers -> headers
                .frameOptions(frame -> frame.disable())
                .contentSecurityPolicy(csp -> csp.policyDirectives(
                        "frame-ancestors 'self'")))
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(csrf -> csrf.csrfTokenRepository(csrfTokenRepository())
                        .csrfTokenRequestHandler(
                                new org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler())
                        // The JWT filter authenticates every request, and Spring's default strategy
                        // then clears the CSRF cookie each time, so the UI had to fetch a new token
                        // before every write. Rotation happens on login and logout instead.
                        .sessionAuthenticationStrategy(
                                new org.springframework.security.web.authentication.session.NullAuthenticatedSessionStrategy()))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(exceptions -> exceptions
                        .accessDeniedHandler((request, response, exception) -> {
                            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                            response.setContentType("application/json;charset=UTF-8");
                            response.getWriter().write("{\"message\":\"Bạn không có quyền thực hiện thao tác này\"}");
                        })
                        .authenticationEntryPoint((request, response, exception) -> response
                                .sendError(HttpServletResponse.SC_UNAUTHORIZED, "Unauthorized")))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/session", "/api/auth/logout").authenticated()
                        .requestMatchers("/api/auth/register", "/api/auth/reset-password-direct").denyAll()
                        .requestMatchers("/api/auth/**").permitAll()
                        .requestMatchers("/ws/**").permitAll()
                        .requestMatchers("/uploads/videos/**").denyAll()
                        .requestMatchers("/api/videos/stream").permitAll()
                        .requestMatchers("/uploads/documents/**").authenticated()
                        .requestMatchers("/api/admin/**").access(adminAuthorization)
                        .anyRequest().authenticated());

        http.addFilterBefore(
                jwtAuthenticationFilter,
                UsernamePasswordAuthenticationFilter.class);

        http.addFilterBefore(
                requestLoggingFilter,
                JwtAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        // Allow dynamic development ports from application configuration
        configuration.setAllowedOrigins(allowedOrigins);
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(Arrays.asList(
                "Content-Type", "X-XSRF-TOKEN", "X-Requested-With", "X-Correlation-Id", "Accept", "Origin", "Range",
                "Access-Control-Request-Method", "Access-Control-Request-Headers"));
        configuration.setExposedHeaders(Arrays.asList(
                "X-Correlation-Id", "Accept-Ranges", "Content-Range", "Content-Length"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
