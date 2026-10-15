package com.bidv.coretraining.security;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Arrays;

@Component
public class JwtCookieService {
    public static final String COOKIE_NAME = "access_token";

    private final Duration maxAge;
    private final boolean secure;
    private final String sameSite;

    public JwtCookieService(
            @Value("${app.jwt.expiration-ms}") long expirationMs,
            @Value("${app.jwt.cookie-secure:false}") boolean secure,
            @Value("${app.jwt.cookie-same-site:Lax}") String sameSite) {
        this.maxAge = Duration.ofMillis(expirationMs);
        this.secure = secure;
        this.sameSite = sameSite;
    }

    public String create(String token) {
        return cookie(token, maxAge).toString();
    }

    public String clear() {
        return cookie("", Duration.ZERO).toString();
    }

    public String resolve(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null)
            return null;
        return Arrays.stream(cookies)
                .filter(cookie -> COOKIE_NAME.equals(cookie.getName()))
                .map(Cookie::getValue)
                .findFirst()
                .orElse(null);
    }

    private ResponseCookie cookie(String value, Duration cookieMaxAge) {
        return ResponseCookie.from(COOKIE_NAME, value)
                .httpOnly(true)
                .secure(secure)
                .sameSite(sameSite)
                .path("/")
                .maxAge(cookieMaxAge)
                .build();
    }
}
