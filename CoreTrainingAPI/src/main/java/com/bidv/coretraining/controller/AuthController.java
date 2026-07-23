package com.bidv.coretraining.controller;

import com.bidv.coretraining.dto.JwtAuthenticationResponse;
import com.bidv.coretraining.dto.LoginRequest;
import com.bidv.coretraining.dto.RegisterRequest;
import com.bidv.coretraining.dto.ForgotPasswordRequest;
import com.bidv.coretraining.dto.ResetPasswordOtpRequest;
import com.bidv.coretraining.service.AuthService;
import com.bidv.coretraining.security.CustomUserDetails;
import com.bidv.coretraining.security.JwtCookieService;
import jakarta.validation.Valid;
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

    public AuthController(AuthService authService, JwtCookieService jwtCookieService) {
        this.authService = authService;
        this.jwtCookieService = jwtCookieService;
    }

    @PostMapping("/login")
    public ResponseEntity<JwtAuthenticationResponse> authenticateUser(@Valid @RequestBody LoginRequest loginRequest) {
        JwtAuthenticationResponse response = authService.authenticateUser(loginRequest);
        String token = response.getAccessToken();
        response.setAccessToken(null);
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, jwtCookieService.create(token))
                .body(response);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout() {
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, jwtCookieService.clear())
                .build();
    }

    @org.springframework.web.bind.annotation.GetMapping("/session")
    public ResponseEntity<JwtAuthenticationResponse> session(
            @AuthenticationPrincipal CustomUserDetails user) {
        return ResponseEntity.ok(JwtAuthenticationResponse.builder()
                .role(user.getUser().getRole())
                .username(user.getUsername())
                .fullName(user.getUser().getFullName())
                .build());
    }

    @PostMapping("/register")
    public ResponseEntity<String> registerUser(@Valid @RequestBody RegisterRequest registerRequest) {
        authService.registerUser(registerRequest);
        return ResponseEntity.ok("User registered successfully");
    }

    @PostMapping("/forgot-password-otp")
    public ResponseEntity<String> forgotPasswordOtp(@Valid @RequestBody ForgotPasswordRequest forgotRequest) {
        authService.sendPasswordResetOtp(forgotRequest);
        return ResponseEntity.ok("Mã xác thực OTP đã được gửi!");
    }

    @PostMapping("/reset-password-otp")
    public ResponseEntity<String> resetPasswordOtp(@Valid @RequestBody ResetPasswordOtpRequest resetRequest) {
        authService.resetPasswordWithOtp(resetRequest);
        return ResponseEntity.ok("Đặt lại mật khẩu thành công!");
    }
}
