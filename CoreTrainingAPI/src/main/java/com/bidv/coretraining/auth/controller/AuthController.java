package com.bidv.coretraining.auth.controller;

import com.bidv.coretraining.auth.dto.ResetPasswordDirectRequest;

import com.bidv.coretraining.auth.dto.CsrfTokenDto;
import com.bidv.coretraining.auth.dto.ForgotPasswordRequest;
import com.bidv.coretraining.auth.dto.JwtAuthenticationResponse;
import com.bidv.coretraining.auth.dto.LoginRequest;
import com.bidv.coretraining.auth.dto.RegisterRequest;
import com.bidv.coretraining.auth.dto.ResetPasswordOtpRequest;
import com.bidv.coretraining.auth.service.AuthService;
import com.bidv.coretraining.security.CustomUserDetails;
import com.bidv.coretraining.security.JwtCookieService;
import com.bidv.coretraining.security.LoginAttemptLimiter;
import org.springframework.security.core.AuthenticationException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService authService;
    private final JwtCookieService jwtCookieService;
    private final CsrfTokenRepository csrfTokens;
    private final LoginAttemptLimiter loginLimiter;

    public AuthController(AuthService authService, JwtCookieService jwtCookieService, CsrfTokenRepository csrfTokens,
            LoginAttemptLimiter loginLimiter) {
        this.authService = authService;
        this.jwtCookieService = jwtCookieService;
        this.csrfTokens = csrfTokens;
        this.loginLimiter = loginLimiter;
    }

    @org.springframework.web.bind.annotation.GetMapping("/csrf")
    public CsrfTokenDto csrf(org.springframework.security.web.csrf.CsrfToken token) {
        return new CsrfTokenDto(token.getToken());
    }

    @PostMapping("/login")
    public ResponseEntity<JwtAuthenticationResponse> authenticateUser(@Valid @RequestBody LoginRequest loginRequest,
            HttpServletRequest request, HttpServletResponse servletResponse) {
        String username = loginRequest.getUsername();
        // With SERVER_FORWARD_HEADERS_STRATEGY=framework this is the first X-Forwarded-For address,
        // so the outer proxy must overwrite that header with the real client address.
        String clientIp = request.getRemoteAddr();
        loginLimiter.checkAllowed(username, clientIp);
        JwtAuthenticationResponse response;
        try {
            response = authService.authenticateUser(loginRequest);
        } catch (AuthenticationException e) {
            loginLimiter.recordFailure(username, clientIp);
            throw e;
        }
        loginLimiter.recordSuccess(username);
        // A token issued before login must not survive it (CSRF token fixation).
        csrfTokens.saveToken(null, request, servletResponse);
        String token = response.getAccessToken();
        response.setAccessToken(null);
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, jwtCookieService.create(token))
                .body(response);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@AuthenticationPrincipal CustomUserDetails user,
            HttpServletRequest request, HttpServletResponse response) {
        if (user != null)
            authService.revokeSessions(user.getUsername());
        csrfTokens.saveToken(null, request, response);
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, jwtCookieService.clear())
                .build();
    }

    @org.springframework.web.bind.annotation.GetMapping("/session")
    public ResponseEntity<JwtAuthenticationResponse> session(
            @AuthenticationPrincipal CustomUserDetails user) {
        return ResponseEntity.ok(authService.session(user));
    }

    @PostMapping("/register")
    public ResponseEntity<String> registerUser(@Valid @RequestBody RegisterRequest registerRequest) {
        authService.registerUser(registerRequest);
        return ResponseEntity.ok("User registered successfully");
    }

    @PostMapping("/forgot-password-otp")
    public ResponseEntity<String> forgotPasswordOtp(@Valid @RequestBody ForgotPasswordRequest forgotRequest) {
        authService.sendPasswordResetOtp(forgotRequest);
        return ResponseEntity.ok("Nếu thông tin hợp lệ, mã xác minh đã được gửi đến email của bạn.");
    }

    @PostMapping("/reset-password-otp")
    public ResponseEntity<String> resetPasswordOtp(@Valid @RequestBody ResetPasswordOtpRequest resetRequest) {
        authService.resetPasswordWithOtp(resetRequest);
        return ResponseEntity.ok("Đặt lại mật khẩu thành công!");
    }

    @PostMapping("/reset-password-direct")
    public ResponseEntity<String> resetPasswordDirect(
            @Valid @RequestBody ResetPasswordDirectRequest resetRequest) {
        authService.resetPasswordDirect(resetRequest);
        return ResponseEntity.ok("Đặt lại mật khẩu thành công!");
    }
}
